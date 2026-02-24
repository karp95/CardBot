package com.cardbot.service;

import com.cardbot.bot.CardTelegramBot;
import com.cardbot.model.LearningStats;
import com.cardbot.model.User;
import com.cardbot.repository.LearningStatsRepository;
import com.cardbot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderService {

    private final UserRepository userRepository;
    private final CardService cardService;
    private final LearningStatsRepository learningStatsRepository;
    private final CardTelegramBot bot;

    /**
     * Напоминание раз в день в 10:00 по UTC.
     * Отправляется пользователям с карточками, которые не учились сегодня.
     */
    @Scheduled(cron = "0 0 10 * * ?")
    @Transactional
    public void sendDailyReminders() {
        Instant startOfToday = Instant.now().atZone(ZoneOffset.UTC)
                .toLocalDate()
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();

        List<User> users = userRepository.findAll();
        int sent = 0;

        for (User user : users) {
            if (cardService.countByUser(user) == 0) {
                continue;
            }

            var statsOpt = learningStatsRepository.findByUser_Id(user.getId());
            boolean learnedToday = statsOpt
                    .map(s -> s.getLastLearnedAt() != null && !s.getLastLearnedAt().isBefore(startOfToday))
                    .orElse(false);
            boolean remindedToday = statsOpt
                    .map(s -> s.getLastReminderAt() != null && !s.getLastReminderAt().isBefore(startOfToday))
                    .orElse(false);

            if (learnedToday || remindedToday) {
                continue;
            }

            if (sendReminder(user)) {
                statsOpt.ifPresent(stats -> {
                    stats.setLastReminderAt(Instant.now());
                    learningStatsRepository.save(stats);
                });
                if (statsOpt.isEmpty()) {
                    var stats = LearningStats.builder()
                            .user(user)
                            .cardsViewedTotal(0L)
                            .lastReminderAt(Instant.now())
                            .build();
                    learningStatsRepository.save(stats);
                }
                sent++;
            }
        }

        log.info("Отправлено напоминаний: {}", sent);
    }

    private boolean sendReminder(User user) {
        String chatId = user.getTelegramId().toString();
        String text = "⏰ Время повторить слова! Нажмите кнопку ниже, чтобы начать.";

        var button = InlineKeyboardButton.builder()
                .text("Учить")
                .callbackData("REMIND_LEARN")
                .build();

        try {
            bot.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(text)
                    .replyMarkup(InlineKeyboardMarkup.builder()
                            .keyboardRow(List.of(button))
                            .build())
                    .build());
            return true;
        } catch (TelegramApiException e) {
            log.warn("Не удалось отправить напоминание пользователю {}: {}", user.getTelegramId(), e.getMessage());
            return false;
        }
    }
}

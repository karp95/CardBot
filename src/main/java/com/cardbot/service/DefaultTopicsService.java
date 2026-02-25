package com.cardbot.service;

import com.cardbot.model.CardSet;
import com.cardbot.model.TemplateCard;
import com.cardbot.model.TemplateSet;
import com.cardbot.model.User;
import com.cardbot.repository.TemplateSetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Стандартные наборы карточек по топикам для новых пользователей.
 * Данные загружаются из БД (таблицы template_sets, template_cards).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultTopicsService {

    private final TemplateSetRepository templateSetRepository;
    private final CardSetService cardSetService;
    private final CardService cardService;

    /**
     * Создаёт стандартные наборы по топикам для пользователя.
     * Добавляет только те наборы, которых ещё нет (для существующих пользователей).
     * @return количество добавленных наборов
     */
    @Transactional
    public int createDefaultSetsForUser(User user) {
        List<TemplateSet> templates = templateSetRepository.findAllWithCardsOrderBySortOrder();
        int added = 0;

        for (TemplateSet template : templates) {
            if (cardSetService.findByNameIgnoreCase(user, template.getName()).isPresent()) {
                continue;
            }
            CardSet set = cardSetService.create(user, template.getName());
            for (TemplateCard tc : template.getCards()) {
                cardService.create(user, set, tc.getWord(), tc.getTranslation(), tc.getTranscription());
            }
            added++;
            log.info("Создан набор «{}» для пользователя {} ({} карточек)", template.getName(), user.getId(), template.getCards().size());
        }
        if (added > 0) {
            log.info("Добавлено {} стандартных наборов для пользователя {}", added, user.getId());
        }
        return added;
    }

    public List<String> getDefaultTopicNames() {
        return templateSetRepository.findAllByOrderBySortOrderAsc().stream()
                .map(TemplateSet::getName)
                .collect(Collectors.toList());
    }
}

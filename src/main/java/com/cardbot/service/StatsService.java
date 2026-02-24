package com.cardbot.service;

import com.cardbot.model.Card;
import com.cardbot.model.User;
import com.cardbot.repository.CardViewRepository;
import com.cardbot.repository.LearningStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatsService {

    private static final int STREAK_LOOKBACK_DAYS = 365;
    private static final int TOP_CARDS_LIMIT = 5;

    private final CardService cardService;
    private final LearningStatsRepository learningStatsRepository;
    private final CardViewRepository cardViewRepository;

    public UserStats getStats(User user) {
        long totalCards = cardService.countByUser(user);
        long cardsViewed = learningStatsRepository.findByUser_Id(user.getId())
                .map(s -> s.getCardsViewedTotal())
                .orElse(0L);

        Instant weekAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        Instant dayStart = Instant.now().truncatedTo(ChronoUnit.DAYS);
        Instant streakSince = Instant.now().minus(STREAK_LOOKBACK_DAYS, ChronoUnit.DAYS);

        List<Instant> viewedAtList = cardViewRepository.findViewedAtByUserIdAndViewedAtSince(user.getId(), streakSince);
        Set<LocalDate> distinctDates = viewedAtList.stream()
                .map(i -> i.atZone(ZoneOffset.UTC).toLocalDate())
                .collect(Collectors.toSet());

        long distinctCardsToday = cardViewRepository.findCardIdsByUserIdAndViewedAtSince(user.getId(), dayStart)
                .stream()
                .distinct()
                .count();
        long distinctCardsThisWeek = cardViewRepository.findCardIdsByUserIdAndViewedAtSince(user.getId(), weekAgo)
                .stream()
                .distinct()
                .count();

        int streak = computeStreak(distinctDates);

        List<Card> topViewed = cardViewRepository.findCardViewCountsByUserId(user.getId(), PageRequest.of(0, TOP_CARDS_LIMIT))
                .stream()
                .map(row -> (Long) row[0])
                .flatMap(cardId -> cardService.findById(cardId).stream())
                .toList();

        return new UserStats(
                totalCards,
                cardsViewed,
                streak,
                distinctCardsToday,
                distinctCardsThisWeek,
                topViewed
        );
    }

    private int computeStreak(Set<LocalDate> distinctDates) {
        if (distinctDates.isEmpty()) {
            return 0;
        }
        List<LocalDate> sorted = distinctDates.stream()
                .sorted(Comparator.reverseOrder())
                .toList();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate mostRecent = sorted.get(0);

        if (mostRecent.isBefore(today.minusDays(1))) {
            return 0;
        }

        int count = 0;
        LocalDate expected = mostRecent;
        for (LocalDate d : sorted) {
            if (d.equals(expected)) {
                count++;
                expected = expected.minusDays(1);
            } else if (d.isBefore(expected)) {
                break;
            }
        }
        return count;
    }

    public record UserStats(
            long totalCards,
            long cardsViewedTotal,
            int streak,
            long distinctCardsToday,
            long distinctCardsThisWeek,
            List<Card> topViewedCards
    ) {}
}

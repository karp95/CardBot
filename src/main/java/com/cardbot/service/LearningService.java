package com.cardbot.service;

import com.cardbot.model.Card;
import com.cardbot.model.CardView;
import com.cardbot.model.LearningStats;
import com.cardbot.model.User;
import com.cardbot.repository.CardViewRepository;
import com.cardbot.repository.LearningStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LearningService {

    private final CardService cardService;
    private final LearningStatsRepository learningStatsRepository;
    private final CardViewRepository cardViewRepository;

    public Optional<Card> getNextCard(User user) {
        return cardService.getRandomCard(user);
    }

    public Optional<Card> getNextCard(User user, Long setId) {
        return cardService.getRandomCard(user, setId);
    }

    public Optional<Card> getNextCardWithoutSet(User user) {
        return cardService.getRandomCardWithoutSet(user);
    }

    @Transactional
    public void incrementCardsViewed(User user, Card card) {
        LearningStats stats = getOrCreateStats(user);
        stats.setCardsViewedTotal(stats.getCardsViewedTotal() + 1);
        stats.setLastLearnedAt(Instant.now());
        learningStatsRepository.save(stats);

        CardView cardView = CardView.builder()
                .user(user)
                .card(card)
                .viewedAt(Instant.now())
                .build();
        cardViewRepository.save(cardView);
    }

    private LearningStats getOrCreateStats(User user) {
        return learningStatsRepository.findByUser_Id(user.getId())
                .orElseGet(() -> {
                    LearningStats stats = LearningStats.builder()
                            .user(user)
                            .cardsViewedTotal(0L)
                            .build();
                    return learningStatsRepository.save(stats);
                });
    }
}

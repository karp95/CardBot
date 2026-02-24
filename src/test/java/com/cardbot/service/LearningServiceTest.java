package com.cardbot.service;

import com.cardbot.model.Card;
import com.cardbot.model.User;

import com.cardbot.repository.LearningStatsRepository;
import com.cardbot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LearningServiceTest {

    @Autowired
    private LearningService learningService;

    @Autowired
    private CardService cardService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LearningStatsRepository learningStatsRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .telegramId(333L)
                .username("learnuser")
                .createdAt(Instant.now())
                .build();
        user = userRepository.save(user);
    }

    @Test
    void getNextCard_shouldReturnRandomCard_whenCardsExist() {
        cardService.create(user, "apple", "яблоко", null);

        var card = learningService.getNextCard(user);

        assertThat(card).isPresent();
        assertThat(card.get().getWord()).isEqualTo("apple");
    }

    @Test
    void getNextCard_shouldReturnEmpty_whenNoCards() {
        var card = learningService.getNextCard(user);
        assertThat(card).isEmpty();
    }

    @Test
    void incrementCardsViewed_shouldCreateStatsAndIncrement() {
        Card card = cardService.create(user, "apple", "яблоко", null);
        learningService.incrementCardsViewed(user, card);

        var stats = learningStatsRepository.findByUser_Id(user.getId());
        assertThat(stats).isPresent();
        assertThat(stats.get().getCardsViewedTotal()).isEqualTo(1L);
    }

    @Test
    void incrementCardsViewed_shouldIncrementExistingStats() {
        Card card = cardService.create(user, "apple", "яблоко", null);
        learningService.incrementCardsViewed(user, card);
        learningService.incrementCardsViewed(user, card);

        var stats = learningStatsRepository.findByUser_Id(user.getId());
        assertThat(stats).isPresent();
        assertThat(stats.get().getCardsViewedTotal()).isEqualTo(2L);
    }
}

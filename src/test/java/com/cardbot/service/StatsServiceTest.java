package com.cardbot.service;

import com.cardbot.model.User;
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
class StatsServiceTest {

    @Autowired
    private StatsService statsService;

    @Autowired
    private CardService cardService;

    @Autowired
    private LearningService learningService;

    @Autowired
    private UserRepository userRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .telegramId(444L)
                .username("statsuser")
                .createdAt(Instant.now())
                .build();
        user = userRepository.save(user);
    }

    @Test
    void getStats_shouldReturnZero_whenNoCards() {
        var stats = statsService.getStats(user);

        assertThat(stats.totalCards()).isEqualTo(0);
        assertThat(stats.cardsViewedTotal()).isEqualTo(0);
        assertThat(stats.streak()).isEqualTo(0);
    }

    @Test
    void getStats_shouldReturnCorrectCounts() {
        var card = cardService.create(user, "apple", "яблоко", null);
        cardService.create(user, "book", "книга", null);
        learningService.incrementCardsViewed(user, card);

        var stats = statsService.getStats(user);

        assertThat(stats.totalCards()).isEqualTo(2);
        assertThat(stats.cardsViewedTotal()).isEqualTo(1);
        assertThat(stats.streak()).isEqualTo(1);
        assertThat(stats.distinctCardsToday()).isEqualTo(1);
    }
}

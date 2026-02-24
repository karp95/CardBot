package com.cardbot.repository;

import com.cardbot.model.LearningStats;
import com.cardbot.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class LearningStatsRepositoryTest {

    @Autowired
    private LearningStatsRepository learningStatsRepository;

    @Autowired
    private UserRepository userRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .telegramId(222L)
                .username("statsuser")
                .createdAt(Instant.now())
                .build();
        user = userRepository.save(user);
    }

    @Test
    void findByUserId_shouldReturnStats_whenExists() {
        LearningStats stats = LearningStats.builder()
                .user(user)
                .cardsViewedTotal(10L)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        learningStatsRepository.save(stats);

        var found = learningStatsRepository.findByUser_Id(user.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getCardsViewedTotal()).isEqualTo(10L);
    }

    @Test
    void findByUserId_shouldReturnEmpty_whenNotExists() {
        var found = learningStatsRepository.findByUser_Id(user.getId());
        assertThat(found).isEmpty();
    }
}

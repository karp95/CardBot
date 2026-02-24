package com.cardbot.bot;

import com.cardbot.model.Card;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LearningSessionTest {

    @Test
    void isGoalReached_shouldReturnFalse_whenNoGoal() {
        var session = LearningSession.create(null, LearningSession.Direction.EN_RU, LearningSession.Order.RANDOM, List.of(), null);
        assertThat(session.isGoalReached()).isFalse();
    }

    @Test
    void isGoalReached_shouldReturnFalse_whenViewedCountLessThanGoal() {
        var session = LearningSession.create(null, LearningSession.Direction.EN_RU, LearningSession.Order.RANDOM, List.of(), 10);
        session.incrementViewedCount();
        session.incrementViewedCount();
        assertThat(session.isGoalReached()).isFalse();
    }

    @Test
    void isGoalReached_shouldReturnTrue_whenViewedCountEqualsGoal() {
        var session = LearningSession.create(null, LearningSession.Direction.EN_RU, LearningSession.Order.RANDOM, List.of(), 10);
        for (int i = 0; i < 10; i++) {
            session.incrementViewedCount();
        }
        assertThat(session.isGoalReached()).isTrue();
    }
}

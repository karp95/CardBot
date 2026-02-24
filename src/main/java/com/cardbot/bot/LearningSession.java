package com.cardbot.bot;

import com.cardbot.model.Card;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class LearningSession {
    public enum Direction { EN_RU, RU_EN }
    public enum Order { RANDOM, SEQUENTIAL }

    private Long setIdFilter; // null=all, -1=none, else setId
    private Direction direction;
    private Order order;
    private List<Card> sequentialCards;
    private int sequentialIndex;
    private Integer goal; // null = no goal
    private int viewedCount; // для цели на сессию

    public static LearningSession create(Long setIdFilter, Direction direction, Order order, List<Card> sequentialCards, Integer goal) {
        LearningSession s = new LearningSession();
        s.setIdFilter = setIdFilter;
        s.direction = direction;
        s.order = order;
        s.sequentialCards = sequentialCards;
        s.sequentialIndex = 0;
        s.goal = goal;
        s.viewedCount = 0;
        return s;
    }

    public void incrementIndex() {
        sequentialIndex++;
    }

    public void incrementViewedCount() {
        viewedCount++;
    }

    public int getViewedCount() {
        return viewedCount;
    }

    public boolean isGoalReached() {
        return goal != null && viewedCount >= goal;
    }
}

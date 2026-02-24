package com.cardbot.bot;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserState {
    public enum Type {
        EDIT_CARD,
        ADD_SET,
        ADD_CARD,
        TYPE_LEARN_INPUT,
        NONE
    }

    private final Type type;
    private final Long cardId;

    public static UserState editing(Long cardId) {
        return new UserState(Type.EDIT_CARD, cardId);
    }

    public static UserState addingSet() {
        return new UserState(Type.ADD_SET, null);
    }

    public static UserState addingCard() {
        return new UserState(Type.ADD_CARD, null);
    }

    public static UserState typeLearnInput(Long cardId) {
        return new UserState(Type.TYPE_LEARN_INPUT, cardId);
    }

    public static UserState none() {
        return new UserState(Type.NONE, null);
    }
}

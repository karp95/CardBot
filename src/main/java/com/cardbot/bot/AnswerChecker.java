package com.cardbot.bot;

/**
 * Проверка ответа пользователя с поддержкой нескольких допустимых вариантов через |.
 */
public final class AnswerChecker {

    private AnswerChecker() {}

    /**
     * Проверяет, совпадает ли ответ пользователя с ожидаемым.
     * Ожидаемый ответ может содержать несколько вариантов через |, например: "идти|ходить".
     * Регистр игнорируется.
     */
    public static boolean isCorrect(String expectedRaw, String actual) {
        if (actual == null || actual.isBlank()) {
            return false;
        }
        String actualTrimmed = actual.trim();
        String[] variants = expectedRaw.split("\\|");
        for (String v : variants) {
            if (v.trim().equalsIgnoreCase(actualTrimmed)) {
                return true;
            }
        }
        return false;
    }
}

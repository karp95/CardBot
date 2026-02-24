package com.cardbot.bot;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerCheckerTest {

    @Test
    void isCorrect_shouldReturnTrue_whenExactMatch() {
        assertThat(AnswerChecker.isCorrect("apple", "apple")).isTrue();
        assertThat(AnswerChecker.isCorrect("идти", "идти")).isTrue();
    }

    @Test
    void isCorrect_shouldReturnTrue_whenMatchIgnoreCase() {
        assertThat(AnswerChecker.isCorrect("apple", "APPLE")).isTrue();
        assertThat(AnswerChecker.isCorrect("Apple", "apple")).isTrue();
    }

    @Test
    void isCorrect_shouldReturnTrue_whenOneOfMultipleVariants() {
        assertThat(AnswerChecker.isCorrect("идти|ходить", "идти")).isTrue();
        assertThat(AnswerChecker.isCorrect("идти|ходить", "ходить")).isTrue();
        assertThat(AnswerChecker.isCorrect("идти|ходить", "ХОДИТЬ")).isTrue();
    }

    @Test
    void isCorrect_shouldReturnFalse_whenNoMatch() {
        assertThat(AnswerChecker.isCorrect("apple", "orange")).isFalse();
        assertThat(AnswerChecker.isCorrect("идти|ходить", "бежать")).isFalse();
    }

    @Test
    void isCorrect_shouldReturnFalse_whenActualNullOrBlank() {
        assertThat(AnswerChecker.isCorrect("apple", null)).isFalse();
        assertThat(AnswerChecker.isCorrect("apple", "")).isFalse();
        assertThat(AnswerChecker.isCorrect("apple", "   ")).isFalse();
    }
}

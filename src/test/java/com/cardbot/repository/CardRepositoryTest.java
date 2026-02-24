package com.cardbot.repository;

import com.cardbot.model.Card;
import com.cardbot.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class CardRepositoryTest {

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private UserRepository userRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .telegramId(111L)
                .username("carduser")
                .createdAt(Instant.now())
                .build();
        user = userRepository.save(user);
    }

    @Test
    void findByUserId_shouldReturnCards() {
        cardRepository.save(createCard("apple", "яблоко"));
        cardRepository.save(createCard("book", "книга"));

        var page = cardRepository.findByUserId(user.getId(), PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void countByUserId_shouldReturnCorrectCount() {
        cardRepository.save(createCard("apple", "яблоко"));
        cardRepository.save(createCard("book", "книга"));

        long count = cardRepository.countByUserId(user.getId());

        assertThat(count).isEqualTo(2);
    }

    @Test
    void findRandomByUserId_shouldReturnRandomCard_whenCardsExist() {
        cardRepository.save(createCard("apple", "яблоко"));
        cardRepository.save(createCard("book", "книга"));

        var random = cardRepository.findRandomByUserId(user.getId());

        assertThat(random).isPresent();
        assertThat(random.get().getUser().getId()).isEqualTo(user.getId());
    }

    @Test
    void findRandomByUserId_shouldReturnEmpty_whenNoCards() {
        var random = cardRepository.findRandomByUserId(user.getId());
        assertThat(random).isEmpty();
    }

    private Card createCard(String word, String translation) {
        return Card.builder()
                .user(user)
                .word(word)
                .translation(translation)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}

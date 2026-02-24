package com.cardbot.service;

import com.cardbot.model.Card;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CardServiceTest {

    @Autowired
    private CardService cardService;

    @Autowired
    private UserRepository userRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .telegramId(111L)
                .username("testuser")
                .createdAt(Instant.now())
                .build();
        user = userRepository.save(user);
    }

    @Test
    void parseCardInput_shouldParseWordAndTranslation() {
        var parsed = cardService.parseCardInput("apple — яблоко");

        assertThat(parsed).isNotNull();
        assertThat(parsed.word()).isEqualTo("apple");
        assertThat(parsed.translation()).isEqualTo("яблоко");
        assertThat(parsed.transcription()).isNull();
    }

    @Test
    void parseCardInput_shouldParseWithDash() {
        var parsed = cardService.parseCardInput("apple - яблоко");

        assertThat(parsed).isNotNull();
        assertThat(parsed.word()).isEqualTo("apple");
        assertThat(parsed.translation()).isEqualTo("яблоко");
    }

    @Test
    void parseCardInput_shouldReturnNull_whenInvalidFormat() {
        assertThat(cardService.parseCardInput("onlyone")).isNull();
        assertThat(cardService.parseCardInput("")).isNull();
        assertThat(cardService.parseCardInput(null)).isNull();
    }

    @Test
    void createFromInput_shouldCreateCard() {
        Card card = cardService.createFromInput(user, "book — книга");

        assertThat(card.getId()).isNotNull();
        assertThat(card.getWord()).isEqualTo("book");
        assertThat(card.getTranslation()).isEqualTo("книга");
    }

    @Test
    void create_shouldStoreTranscription() {
        Card card = cardService.create(user, "apple", "яблоко", "ˈæpl");

        assertThat(card.getTranscription()).isEqualTo("ˈæpl");
        var found = cardService.findById(card.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getTranscription()).isEqualTo("ˈæpl");
    }

    @Test
    void createFromInput_shouldThrow_whenInvalidFormat() {
        assertThatThrownBy(() -> cardService.createFromInput(user, "invalid"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void findByIdAndUserId_shouldReturnCard_whenBelongsToUser() {
        Card card = cardService.create(user, "cat", "кошка", null);

        var found = cardService.findByIdAndUserId(card.getId(), user.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getWord()).isEqualTo("cat");
    }

    @Test
    void findByIdAndUserId_shouldReturnEmpty_whenDifferentUser() {
        Card card = cardService.create(user, "cat", "кошка", null);
        User otherUser = userRepository.save(User.builder()
                .telegramId(999L)
                .username("other")
                .createdAt(Instant.now())
                .build());

        var found = cardService.findByIdAndUserId(card.getId(), otherUser.getId());

        assertThat(found).isEmpty();
    }

    @Test
    void update_shouldUpdateCard() {
        Card card = cardService.create(user, "old", "старый", null);

        Card updated = cardService.update(card, "new", "новый", "njuː");

        assertThat(updated.getWord()).isEqualTo("new");
        assertThat(updated.getTranslation()).isEqualTo("новый");
        assertThat(updated.getTranscription()).isEqualTo("njuː");
    }

    @Test
    void delete_shouldRemoveCard() {
        Card card = cardService.create(user, "temp", "временный", null);
        Long id = card.getId();

        cardService.delete(card);

        assertThat(cardService.findById(id)).isEmpty();
    }
}

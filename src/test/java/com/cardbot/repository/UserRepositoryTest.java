package com.cardbot.repository;

import com.cardbot.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByTelegramId_shouldReturnUser_whenExists() {
        User user = User.builder()
                .telegramId(123456789L)
                .username("testuser")
                .createdAt(Instant.now())
                .build();
        userRepository.save(user);

        var found = userRepository.findByTelegramId(123456789L);

        assertThat(found).isPresent();
        assertThat(found.get().getTelegramId()).isEqualTo(123456789L);
        assertThat(found.get().getUsername()).isEqualTo("testuser");
    }

    @Test
    void findByTelegramId_shouldReturnEmpty_whenNotExists() {
        var found = userRepository.findByTelegramId(999999L);
        assertThat(found).isEmpty();
    }
}

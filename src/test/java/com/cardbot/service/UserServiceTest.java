package com.cardbot.service;

import com.cardbot.model.User;
import com.cardbot.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void getOrCreate_shouldCreateNewUser_whenNotExists() {
        User user = userService.getOrCreate(12345L, "newuser");

        assertThat(user.getId()).isNotNull();
        assertThat(user.getTelegramId()).isEqualTo(12345L);
        assertThat(user.getUsername()).isEqualTo("newuser");
    }

    @Test
    void getOrCreate_shouldReturnExistingUser_whenExists() {
        User first = userService.getOrCreate(67890L, "first");
        User second = userService.getOrCreate(67890L, "updated");

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(userRepository.count()).isEqualTo(1);
    }
}

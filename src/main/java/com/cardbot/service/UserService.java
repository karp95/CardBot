package com.cardbot.service;

import com.cardbot.model.User;
import com.cardbot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public User getOrCreate(Long telegramId, String username) {
        return userRepository.findByTelegramId(telegramId)
                .orElseGet(() -> {
                    User user = User.builder()
                            .telegramId(telegramId)
                            .username(username)
                            .build();
                    return userRepository.save(user);
                });
    }
}

package com.cardbot.service;

import com.cardbot.model.CardSet;
import com.cardbot.model.User;
import com.cardbot.repository.CardSetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CardSetService {

    private final CardSetRepository cardSetRepository;

    public List<CardSet> findAllByUser(User user) {
        return cardSetRepository.findByUserIdOrderByName(user.getId());
    }

    public Optional<CardSet> findByIdAndUserId(Long id, Long userId) {
        return cardSetRepository.findByIdAndUserId(id, userId);
    }

    public Optional<CardSet> findByNameIgnoreCase(User user, String name) {
        return cardSetRepository.findByUserIdAndNameIgnoreCase(user.getId(), name.trim());
    }

    @Transactional
    public CardSet getOrCreate(User user, String name) {
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return cardSetRepository.findByUserIdAndNameIgnoreCase(user.getId(), trimmed)
                .orElseGet(() -> {
                    CardSet set = CardSet.builder()
                            .user(user)
                            .name(trimmed)
                            .build();
                    return cardSetRepository.save(set);
                });
    }

    @Transactional
    public CardSet create(User user, String name) {
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Название набора не может быть пустым");
        }
        if (cardSetRepository.existsByUserIdAndNameIgnoreCase(user.getId(), trimmed)) {
            throw new IllegalArgumentException("Набор «" + trimmed + "» уже существует");
        }
        CardSet set = CardSet.builder()
                                .user(user)
                                .name(trimmed)
                                .build();
        return cardSetRepository.save(set);
    }

    @Transactional
    public void delete(CardSet cardSet) {
        cardSetRepository.delete(cardSet);
    }
}

package com.cardbot.repository;

import com.cardbot.model.CardSet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CardSetRepository extends JpaRepository<CardSet, Long> {

    List<CardSet> findByUserIdOrderByName(Long userId);

    Optional<CardSet> findByIdAndUserId(Long id, Long userId);

    Optional<CardSet> findByUserIdAndNameIgnoreCase(Long userId, String name);

    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);
}

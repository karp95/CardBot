package com.cardbot.repository;

import com.cardbot.model.Card;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CardRepository extends JpaRepository<Card, Long> {

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"cardSet"})
    Page<Card> findByUserId(Long userId, Pageable pageable);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"cardSet"})
    Page<Card> findByUserIdAndCardSet_Id(Long userId, Long setId, Pageable pageable);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"cardSet"})
    Page<Card> findByUserIdAndCardSetIsNull(Long userId, Pageable pageable);

    long countByUserId(Long userId);

    long countByUserIdAndCardSet_Id(Long userId, Long setId);

    long countByUserIdAndCardSetIsNull(Long userId);

    @Query(value = "SELECT * FROM cards WHERE user_id = :userId ORDER BY RANDOM() LIMIT 1", nativeQuery = true)
    Optional<Card> findRandomByUserId(@Param("userId") Long userId);

    @Query(value = "SELECT * FROM cards WHERE user_id = :userId AND set_id = :setId ORDER BY RANDOM() LIMIT 1", nativeQuery = true)
    Optional<Card> findRandomByUserIdAndCardSetId(@Param("userId") Long userId, @Param("setId") Long setId);

    @Query(value = "SELECT * FROM cards WHERE user_id = :userId AND set_id IS NULL ORDER BY RANDOM() LIMIT 1", nativeQuery = true)
    Optional<Card> findRandomByUserIdAndCardSetIsNull(@Param("userId") Long userId);

    Page<Card> findByUserIdOrderByIdAsc(Long userId, Pageable pageable);

    Page<Card> findByUserIdAndCardSet_IdOrderByIdAsc(Long userId, Long setId, Pageable pageable);

    Page<Card> findByUserIdAndCardSetIsNullOrderByIdAsc(Long userId, Pageable pageable);
}

package com.cardbot.repository;

import com.cardbot.model.CardView;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface CardViewRepository extends JpaRepository<CardView, Long> {

    @Query("""
            SELECT cv.viewedAt FROM CardView cv
            WHERE cv.user.id = :userId AND cv.viewedAt >= :since
            """)
    List<Instant> findViewedAtByUserIdAndViewedAtSince(@Param("userId") Long userId, @Param("since") Instant since);

    @Query("""
            SELECT cv.card.id FROM CardView cv
            WHERE cv.user.id = :userId AND cv.viewedAt >= :since
            """)
    List<Long> findCardIdsByUserIdAndViewedAtSince(@Param("userId") Long userId, @Param("since") Instant since);

    @Query("""
            SELECT cv.card.id, COUNT(cv)
            FROM CardView cv
            WHERE cv.user.id = :userId
            GROUP BY cv.card.id
            ORDER BY COUNT(cv) DESC
            """)
    List<Object[]> findCardViewCountsByUserId(@Param("userId") Long userId, Pageable pageable);
}

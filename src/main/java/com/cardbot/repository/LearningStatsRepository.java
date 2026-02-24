package com.cardbot.repository;

import com.cardbot.model.LearningStats;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LearningStatsRepository extends JpaRepository<LearningStats, Long> {

    Optional<LearningStats> findByUser_Id(Long userId);
}

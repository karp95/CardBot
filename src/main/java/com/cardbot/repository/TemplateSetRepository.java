package com.cardbot.repository;

import com.cardbot.model.TemplateSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TemplateSetRepository extends JpaRepository<TemplateSet, Long> {

    List<TemplateSet> findAllByOrderBySortOrderAsc();

    @Query("SELECT ts FROM TemplateSet ts LEFT JOIN FETCH ts.cards ORDER BY ts.sortOrder")
    List<TemplateSet> findAllWithCardsOrderBySortOrder();
}

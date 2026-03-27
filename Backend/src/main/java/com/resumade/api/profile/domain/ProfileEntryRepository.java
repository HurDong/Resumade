package com.resumade.api.profile.domain;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProfileEntryRepository extends JpaRepository<ProfileEntry, String> {

    List<ProfileEntry> findAllByOrderBySortOrderAscCreatedAtAsc();

    @Query("SELECT COALESCE(MAX(e.sortOrder), -1) FROM ProfileEntry e WHERE e.category = :category")
    int findMaxSortOrderByCategory(@Param("category") ProfileCategory category);
}

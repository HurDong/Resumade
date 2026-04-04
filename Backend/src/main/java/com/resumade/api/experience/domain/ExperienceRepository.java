package com.resumade.api.experience.domain;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExperienceRepository extends JpaRepository<Experience, Long> {

    @Query("select distinct e from Experience e left join fetch e.facets")
    List<Experience> findAllWithFacets();

    @Query("select distinct e from Experience e left join fetch e.facets where e.id = :id")
    Optional<Experience> findByIdWithFacets(@Param("id") Long id);
}

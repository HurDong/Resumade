package com.resumade.api.experience.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExperienceUnitRepository extends JpaRepository<ExperienceUnit, Long> {

    @Query("""
            select u
            from ExperienceUnit u
            join fetch u.experience e
            left join fetch u.facet f
            order by e.id asc, f.displayOrder asc, u.displayOrder asc, u.id asc
            """)
    List<ExperienceUnit> findAllWithExperienceAndFacet();

    List<ExperienceUnit> findByExperienceIdOrderByDisplayOrderAsc(Long experienceId);

    void deleteByExperienceId(Long experienceId);
}

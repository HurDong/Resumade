package com.resumade.api.workspace.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ApplicationScheduleRepository extends JpaRepository<ApplicationSchedule, Long> {

    List<ApplicationSchedule> findByApplicationIdOrderBySortOrderAsc(Long applicationId);

    @Query("""
        SELECT s FROM ApplicationSchedule s
        WHERE s.scheduledAt BETWEEN :start AND :end
        ORDER BY s.scheduledAt ASC
    """)
    List<ApplicationSchedule> findByScheduledAtBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    void deleteByApplicationId(Long applicationId);
}

package com.resumade.api.workspace.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, Long> {

    List<Application> findByDeadlineBetween(LocalDateTime start, LocalDateTime end);

    @Query("select distinct a from Application a left join fetch a.questions")
    List<Application> findAllWithQuestions();

    @Query("select distinct a from Application a left join fetch a.questions where a.id = :id")
    Optional<Application> findByIdWithQuestions(@Param("id") Long id);
}

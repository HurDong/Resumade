package com.resumade.api.workspace.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkspaceQuestionRepository extends JpaRepository<WorkspaceQuestion, Long> {
    List<WorkspaceQuestion> findByApplicationId(Long applicationId);

    List<WorkspaceQuestion> findByApplicationIdOrderByIdAsc(Long applicationId);

    @Query("select q from WorkspaceQuestion q join fetch q.application where q.id = :id")
    Optional<WorkspaceQuestion> findByIdWithApplication(@Param("id") Long id);
}

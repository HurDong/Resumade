package com.resumade.api.workspace.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkspaceQuestionRepository extends JpaRepository<WorkspaceQuestion, Long> {
    List<WorkspaceQuestion> findByApplicationId(Long applicationId);
}

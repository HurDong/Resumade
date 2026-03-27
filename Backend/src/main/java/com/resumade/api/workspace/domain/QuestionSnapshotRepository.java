package com.resumade.api.workspace.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuestionSnapshotRepository extends JpaRepository<QuestionSnapshot, Long> {

    List<QuestionSnapshot> findByQuestionIdOrderByCreatedAtDesc(Long questionId);

    long countByQuestionId(Long questionId);

    @Modifying
    @Query("""
        DELETE FROM QuestionSnapshot s
        WHERE s.questionId = :questionId
          AND s.id NOT IN (
              SELECT s2.id FROM QuestionSnapshot s2
              WHERE s2.questionId = :questionId
              ORDER BY s2.createdAt DESC
              LIMIT :keepCount
          )
        """)
    void deleteOldSnapshots(@Param("questionId") Long questionId,
                            @Param("keepCount") int keepCount);
}

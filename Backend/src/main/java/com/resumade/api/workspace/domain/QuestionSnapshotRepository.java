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
    @Query(value = """
        DELETE FROM question_snapshots
        WHERE question_id = :questionId
          AND id NOT IN (
              SELECT id FROM (
                  SELECT id FROM question_snapshots
                  WHERE question_id = :questionId
                  ORDER BY created_at DESC
                  LIMIT :keepCount
              ) AS keep_ids
          )
        """, nativeQuery = true)
    void deleteOldSnapshots(@Param("questionId") Long questionId,
                            @Param("keepCount") int keepCount);
}

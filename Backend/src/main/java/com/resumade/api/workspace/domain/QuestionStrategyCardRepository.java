package com.resumade.api.workspace.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QuestionStrategyCardRepository extends JpaRepository<QuestionStrategyCard, Long> {

    Optional<QuestionStrategyCard> findByQuestionId(Long questionId);
}

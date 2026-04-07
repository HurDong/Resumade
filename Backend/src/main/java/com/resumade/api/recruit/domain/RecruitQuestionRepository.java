package com.resumade.api.recruit.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecruitQuestionRepository extends JpaRepository<RecruitQuestion, Long> {
}

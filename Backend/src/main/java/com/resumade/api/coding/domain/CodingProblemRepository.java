package com.resumade.api.coding.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CodingProblemRepository extends JpaRepository<CodingProblem, Long> {

    List<CodingProblem> findAllByOrderByDateDescCreatedAtDesc();
}

package com.resumade.api.recruit.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RecruitRepository extends JpaRepository<Recruit, Long> {
    Optional<Recruit> findByJasoseolId(Long jasoseolId);
    
    // 이달에 시작하거나 끝나는 공고를 가져오기 위한 쿼리
    List<Recruit> findByStartTimeBetweenOrEndTimeBetween(LocalDateTime start1, LocalDateTime end1, LocalDateTime start2, LocalDateTime end2);
}

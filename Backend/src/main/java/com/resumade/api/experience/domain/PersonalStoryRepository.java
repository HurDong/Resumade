package com.resumade.api.experience.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * {@link PersonalStory} 엔티티를 위한 JPA 레포지토리.
 */
@Repository
public interface PersonalStoryRepository extends JpaRepository<PersonalStory, Long> {
    List<PersonalStory> findAllByOrderByIdAsc();
}

package com.resumade.api.technote.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TechNoteRepository extends JpaRepository<TechNote, Long> {}

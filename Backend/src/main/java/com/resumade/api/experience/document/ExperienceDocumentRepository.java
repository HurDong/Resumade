package com.resumade.api.experience.document;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExperienceDocumentRepository extends ElasticsearchRepository<ExperienceDocument, String> {
    List<ExperienceDocument> findByExperienceId(Long experienceId);
    void deleteByExperienceId(Long experienceId);
}

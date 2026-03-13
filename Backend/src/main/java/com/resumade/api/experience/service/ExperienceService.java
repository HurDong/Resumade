package com.resumade.api.experience.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumade.api.experience.document.ExperienceDocument;
import com.resumade.api.experience.document.ExperienceDocumentRepository;
import com.resumade.api.experience.domain.Experience;
import com.resumade.api.experience.domain.ExperienceRepository;
import com.resumade.api.experience.dto.ExperienceResponse;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.data.embedding.Embedding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExperienceService {

    private final ExperienceRepository experienceRepository;
    private final ExperienceDocumentRepository experienceDocumentRepository;
    private final ObjectMapper objectMapper;

    @Value("${openai.api.key:demo}")
    private String openAiApiKey;

    @Transactional
    public ExperienceResponse uploadAndIndexExperience(MultipartFile file, String title, String category, String period, String role) throws Exception {
        // 1. Parse File Content
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);

        // TODO: In a real scenario, use LLM to extract desc, techStack, and metrics from the raw content.
        // Mocking extraction for initial setup.
        List<String> mockTech = List.of("Spring", "Java", "Docker");
        List<String> mockMetrics = List.of("Performance +30%", "Uptime 99.9%");
        String mockDesc = content.length() > 200 ? content.substring(0, 200) + "..." : content;

        // 2. Save to MySQL
        Experience experience = Experience.builder()
                .title(title)
                .category(category)
                .description(mockDesc)
                .techStack(objectMapper.writeValueAsString(mockTech))
                .metrics(objectMapper.writeValueAsString(mockMetrics))
                .period(period)
                .role(role)
                .originalFileName(file.getOriginalFilename())
                .build();
        Experience savedExperience = experienceRepository.save(experience);

        // 3. Chunking & Embedding for Elasticsearch
        indexToElasticsearch(savedExperience.getId(), content);

        return ExperienceResponse.from(savedExperience, mockTech, mockMetrics);
    }

    private void indexToElasticsearch(Long experienceId, String content) {
        // Limit API calls if key is default/demo
        if("demo".equals(openAiApiKey)) {
            log.warn("OpenAI API Key not set. Skipping Elasticsearch embedding to save costs/prevent errors.");
            return;
        }

        Document document = Document.from(content);
        
        // Chunking Strategy: 500 max characters per chunk, 50 characters overlap
        List<TextSegment> segments = DocumentSplitters.recursive(500, 50).split(document);

        // Init OpenAi Embedding Model
        EmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
                .apiKey(openAiApiKey)
                .modelName("text-embedding-3-small")
                .build();

        List<ExperienceDocument> esDocuments = new ArrayList<>();
        for (TextSegment segment : segments) {
            String text = segment.text();
            Embedding embedding = embeddingModel.embed(text).content();

            ExperienceDocument esDoc = ExperienceDocument.builder()
                    .experienceId(experienceId)
                    .chunkText(text)
                    .embedding(embedding.vector())
                    .build();
            esDocuments.add(esDoc);
        }

        // 4. Save to Elasticsearch
        experienceDocumentRepository.saveAll(esDocuments);
        log.info("Indexed {} chunks for experience id {}", segments.size(), experienceId);
    }

    @Transactional(readOnly = true)
    public List<ExperienceResponse> getAllExperiences() {
        return experienceRepository.findAll().stream()
                .map(exp -> {
                    try {
                        List<String> tech = objectMapper.readValue(exp.getTechStack(), new TypeReference<>() {});
                        List<String> metrics = objectMapper.readValue(exp.getMetrics(), new TypeReference<>() {});
                        return ExperienceResponse.from(exp, tech, metrics);
                    } catch (JsonProcessingException e) {
                        return ExperienceResponse.from(exp, List.of(), List.of());
                    }
                })
                .collect(Collectors.toList());
    }
}

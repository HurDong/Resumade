package com.resumade.api.experience.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumade.api.experience.document.ExperienceDocument;
import com.resumade.api.experience.document.ExperienceDocumentRepository;
import com.resumade.api.experience.dto.ExperienceExtractionResult;
import com.resumade.api.experience.domain.Experience;
import com.resumade.api.experience.domain.ExperienceRepository;
import com.resumade.api.experience.dto.ExperienceResponse;
import dev.langchain4j.data.document.Document;
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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExperienceService {

    private static final List<String> SUPPORTED_EXTENSIONS = List.of(".md", ".json");

    private final ExperienceRepository experienceRepository;
    private final ExperienceDocumentRepository experienceDocumentRepository;
    private final ExperienceAiService experienceAiService;
    private final ObjectMapper objectMapper;

    @Value("${openai.api.key:demo}")
    private String openAiApiKey;

    @Transactional
    public ExperienceResponse uploadAndIndexExperience(MultipartFile file, String title, String category, String period, String role) throws Exception {
        validateSupportedFileType(file);

        // 1. Parse File Content
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);

        // 2. Real AI Extraction
        ExperienceExtractionResult aiResult = experienceAiService.extractExperience(content);

        // 3. Save to MySQL
        Experience experience = Experience.builder()
                .title(aiResult.getTitle() != null ? aiResult.getTitle() : title)
                .category(aiResult.getCategory() != null ? aiResult.getCategory() : category)
                .description(aiResult.getDescription())
                .techStack(objectMapper.writeValueAsString(aiResult.getTechStack()))
                .metrics(objectMapper.writeValueAsString(aiResult.getMetrics()))
                .period(aiResult.getPeriod() != null ? aiResult.getPeriod() : period)
                .role(aiResult.getRole() != null ? aiResult.getRole() : role)
                .organization(aiResult.getOrganization())
                .rawContent(content)
                .originalFileName(file.getOriginalFilename())
                .build();
        Experience savedExperience = experienceRepository.save(experience);

        // 4. Chunking & Embedding for Elasticsearch - Use structured text for better RAG
        String narrativeText = buildNarrativeText(savedExperience, aiResult.getTechStack(), aiResult.getMetrics())
                + "\nStructured raw content:\n" + content;
        indexToElasticsearch(savedExperience.getId(), narrativeText);

        return ExperienceResponse.from(savedExperience, aiResult.getTechStack(), aiResult.getMetrics());
    }

    @Transactional
    public void reclassifyAll() {
        List<Experience> allExperiences = experienceRepository.findAll();
        for (Experience exp : allExperiences) {
            reclassify(exp);
        }
    }

    private void reclassify(Experience experience) {
        if (experience.getRawContent() == null || experience.getRawContent().isEmpty()) {
            log.warn("No raw content for experience id {}, skipping reclassification", experience.getId());
            return;
        }

        try {
            ExperienceExtractionResult aiResult = experienceAiService.extractExperience(experience.getRawContent());
            experience.updateFromAi(
                    aiResult.getTitle(),
                    aiResult.getCategory(),
                    aiResult.getDescription(),
                    objectMapper.writeValueAsString(aiResult.getTechStack()),
                    objectMapper.writeValueAsString(aiResult.getMetrics()),
                    aiResult.getPeriod(),
                    aiResult.getRole(),
                    aiResult.getOrganization()
            );
            experienceRepository.save(experience);
            // Update ES index when reclassifying
            experienceDocumentRepository.deleteByExperienceId(experience.getId());
            String narrativeText = buildNarrativeText(experience, aiResult.getTechStack(), aiResult.getMetrics())
                    + "\nStructured raw content:\n" + experience.getRawContent();
            indexToElasticsearch(experience.getId(), narrativeText);

            log.info("Reclassified experience id {}", experience.getId());
        } catch (Exception e) {
            log.error("Failed to reclassify experience id {}", experience.getId(), e);
        }
    }

    private void validateSupportedFileType(MultipartFile file) {
        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null) {
            throw new IllegalArgumentException("파일 이름이 유효하지 않습니다.");
        }

        String lowerCaseFileName = originalFileName.toLowerCase(Locale.ROOT);
        boolean isSupported = SUPPORTED_EXTENSIONS.stream().anyMatch(lowerCaseFileName::endsWith);
        if (!isSupported) {
            throw new IllegalArgumentException("지원하지 않는 파일 형식입니다 (.md, .json만 가능): " + originalFileName);
        }
    }

    private void indexToElasticsearch(Long experienceId, String content) {
        // Limit API calls if key is default/demo
        if ("demo".equals(openAiApiKey)) {
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

    @Transactional
    public ExperienceResponse reclassifySingle(Long id) {
        Experience experience = experienceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Experience not found: " + id));
        reclassify(experience);
        try {
            List<String> tech = objectMapper.readValue(experience.getTechStack(), new TypeReference<>() {});
            List<String> metrics = objectMapper.readValue(experience.getMetrics(), new TypeReference<>() {});
            return ExperienceResponse.from(experience, tech, metrics);
        } catch (JsonProcessingException e) {
            return ExperienceResponse.from(experience, List.of(), List.of());
        }
    }

    @Transactional
    public ExperienceResponse updateRawContent(Long id, String rawContent) {
        Experience experience = experienceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Experience not found: " + id));

        String normalizedRawContent = rawContent == null ? "" : rawContent.trim();
        if (normalizedRawContent.isBlank()) {
            throw new IllegalArgumentException("Markdown content must not be blank.");
        }

        MarkdownSnapshot snapshot = parseMarkdownSnapshot(normalizedRawContent);
        List<String> techStack = snapshot.techStack().isEmpty()
                ? readJsonArray(experience.getTechStack())
                : snapshot.techStack();
        List<String> metrics = snapshot.metrics().isEmpty()
                ? readJsonArray(experience.getMetrics())
                : snapshot.metrics();
        String techStackJson = writeJsonArray(techStack);
        String metricsJson = writeJsonArray(metrics);

        experience.updateFromMarkdown(
                snapshot.title().isBlank() ? experience.getTitle() : snapshot.title(),
                snapshot.description().isBlank() ? experience.getDescription() : snapshot.description(),
                techStackJson,
                metricsJson,
                snapshot.period().isBlank() ? experience.getPeriod() : snapshot.period(),
                snapshot.role().isBlank() ? experience.getRole() : snapshot.role(),
                snapshot.organization().isBlank() ? experience.getOrganization() : snapshot.organization(),
                normalizedRawContent
        );

        experienceDocumentRepository.deleteByExperienceId(id);
        String narrativeText = buildNarrativeText(experience, techStack, metrics)
                + "\nStructured raw content:\n" + normalizedRawContent;
        indexToElasticsearch(id, narrativeText);

        Experience saved = experienceRepository.save(experience);
        return ExperienceResponse.from(saved, techStack, metrics);
    }

    @Transactional
    public void deleteExperience(Long id) {
        if (!experienceRepository.existsById(id)) {
            throw new IllegalArgumentException("Experience not found: " + id);
        }
        
        try {
            experienceDocumentRepository.deleteByExperienceId(id);
        } catch (Exception e) {
            log.error("Failed to delete Elasticsearch documents for experience id: {}", id, e);
        }

        experienceRepository.deleteById(id);
        log.info("Deleted experience id: {}", id);
    }

    private String writeJsonArray(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize markdown-derived list fields.", e);
        }
    }

    private List<String> readJsonArray(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse JSON array from stored experience field", e);
            return List.of();
        }
    }

    private MarkdownSnapshot parseMarkdownSnapshot(String rawContent) {
        String[] lines = rawContent.replace("\r\n", "\n").split("\n", -1);
        String title = "";
        Map<String, StringBuilder> sections = new LinkedHashMap<>();
        String currentSection = null;

        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine;
            String trimmed = line.trim();

            if (trimmed.startsWith("# ") && title.isBlank()) {
                title = trimmed.substring(2).trim();
                continue;
            }

            if (trimmed.startsWith("## ")) {
                currentSection = normalizeHeading(trimmed.substring(3));
                sections.putIfAbsent(currentSection, new StringBuilder());
                continue;
            }

            if (currentSection != null) {
                StringBuilder builder = sections.get(currentSection);
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(line);
            }
        }

        String description = extractParagraph(sections, "프로젝트개요", "개요", "summary");
        List<String> techStack = extractList(sections, "기술스택", "스택", "techstack");
        List<String> metrics = extractList(sections, "주요성과", "성과", "metrics");
        String period = extractSingleLine(sections, "프로젝트기간", "기간", "period");
        String role = extractSingleLine(sections, "담당역할", "역할", "role");
        String organization = extractSingleLine(sections, "소속기관", "소속", "organization", "기관");

        return new MarkdownSnapshot(title, description, techStack, metrics, period, role, organization);
    }

    private String normalizeHeading(String heading) {
        if (heading == null) {
            return "";
        }

        return heading
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^0-9a-zA-Z가-힣]", "");
    }

    private String extractParagraph(Map<String, StringBuilder> sections, String... keys) {
        for (String key : keys) {
            String value = getSectionText(sections, key);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String extractSingleLine(Map<String, StringBuilder> sections, String... keys) {
        String paragraph = extractParagraph(sections, keys);
        if (paragraph.isBlank()) {
            return "";
        }

        return paragraph.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .findFirst()
                .orElse("");
    }

    private List<String> extractList(Map<String, StringBuilder> sections, String... keys) {
        String text = extractParagraph(sections, keys);
        if (text.isBlank()) {
            return List.of();
        }

        return text.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .map(line -> line.replaceFirst("^[-*]\\s*", "").trim())
                .filter(line -> !line.isBlank())
                .collect(Collectors.toList());
    }

    private String getSectionText(Map<String, StringBuilder> sections, String key) {
        StringBuilder builder = sections.get(key);
        return builder == null ? "" : builder.toString().trim();
    }

    private String buildNarrativeText(Experience exp, List<String> techStack, List<String> metrics) {
        StringBuilder sb = new StringBuilder();
        sb.append("제목: ").append(exp.getTitle()).append("\n");
        if (exp.getOrganization() != null && !exp.getOrganization().isBlank()) {
            sb.append("소속/맥락: ").append(exp.getOrganization()).append("\n");
        }
        if (exp.getRole() != null && !exp.getRole().isBlank()) {
            sb.append("역할: ").append(exp.getRole()).append("\n");
        }
        if (exp.getPeriod() != null && !exp.getPeriod().isBlank()) {
            sb.append("기간: ").append(exp.getPeriod()).append("\n");
        }
        if (exp.getDescription() != null && !exp.getDescription().isBlank()) {
            sb.append("상세내용: ").append(exp.getDescription()).append("\n");
        }
        if (techStack != null && !techStack.isEmpty()) {
            sb.append("기술스택: ").append(String.join(", ", techStack)).append("\n");
        }
        if (metrics != null && !metrics.isEmpty()) {
            sb.append("성과항목: ").append(String.join(", ", metrics)).append("\n");
        }
        return sb.toString();
    }

    private record MarkdownSnapshot(
            String title,
            String description,
            List<String> techStack,
            List<String> metrics,
            String period,
            String role,
            String organization
    ) {
    }
}

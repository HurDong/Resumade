package com.resumade.api.experience.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumade.api.experience.document.ExperienceDocument;
import com.resumade.api.experience.document.ExperienceDocumentRepository;
import com.resumade.api.experience.domain.Experience;
import com.resumade.api.experience.domain.ExperienceFacet;
import com.resumade.api.experience.domain.ExperienceUnit;
import com.resumade.api.experience.domain.ExperienceRepository;
import com.resumade.api.experience.dto.ExperienceExtractionResult;
import com.resumade.api.experience.dto.ExperienceFacetResponse;
import com.resumade.api.experience.dto.ExperienceResponse;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExperienceService {

    private static final List<String> SUPPORTED_EXTENSIONS = List.of(".md", ".json");
    private static final Pattern FILE_CITATION_PATTERN = Pattern.compile("?fileciteturn\\d+file\\d+(?:-L\\d+)?");
    private static final Pattern BRACKET_CITATION_PATTERN = Pattern.compile("【\\d+:\\d+†[^】]+】");
    private static final Pattern ZERO_WIDTH_PATTERN = Pattern.compile("[\\u200B-\\u200D\\u2060\\uFEFF]");
    private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile("[\\t\\u000B\\f\\r ]{2,}");
    private static final Pattern FACET_HEADING_PATTERN = Pattern.compile("(?i)^facet(?:\\s+\\d+)?\\s*(?:[|:-]\\s*)?(.*)$");

    private final ExperienceRepository experienceRepository;
    private final ExperienceDocumentRepository experienceDocumentRepository;
    private final ExperienceAiService experienceAiService;
    private final ExperienceUnitFactory experienceUnitFactory;
    private final ElasticsearchOperations elasticsearchOperations;
    private final ObjectMapper objectMapper;

    @Value("${openai.api.key:demo}")
    private String openAiApiKey;

    @Transactional
    public ExperienceResponse uploadAndIndexExperience(
            MultipartFile file,
            String title,
            String category,
            String period,
            String role
    ) throws Exception {
        validateSupportedFileType(file);

        String content = sanitizeRawContent(new String(file.getBytes(), StandardCharsets.UTF_8));
        ExperienceStructuredSnapshot snapshot = parseStructuredUpload(content, title, category, period, role);

        Experience experience = Experience.builder()
                .title(snapshot.title())
                .category(snapshot.category())
                .description(snapshot.summary())
                .techStack(writeJsonArray(snapshot.aggregatedTechStack()))
                .metrics(writeJsonArray(snapshot.aggregatedResults()))
                .origin(snapshot.origin())
                .overallTechStack(writeJsonArray(snapshot.overallTechStack()))
                .jobKeywords(writeJsonArray(snapshot.jobKeywords()))
                .questionTypes(writeJsonArray(snapshot.questionTypes()))
                .period(snapshot.period())
                .role(snapshot.role())
                .organization(snapshot.organization())
                .rawContent(content)
                .originalFileName(firstNonBlank(file.getOriginalFilename(), snapshot.title()))
                .build();
        experience.replaceFacets(buildFacetEntities(snapshot.facets()));
        experience.replaceUnits(experienceUnitFactory.buildUnits(experience));

        Experience savedExperience = experienceRepository.save(experience);
        reindexExperience(savedExperience);

        Experience hydrated = experienceRepository.findByIdWithFacets(savedExperience.getId())
                .orElse(savedExperience);
        return buildResponse(hydrated);
    }

    @Transactional
    public void reclassifyAll() {
        List<Experience> allExperiences = experienceRepository.findAllWithFacets();
        for (Experience experience : allExperiences) {
            reclassify(experience);
        }
    }

    private void reclassify(Experience experience) {
        if (experience.getRawContent() == null || experience.getRawContent().isBlank()) {
            log.warn("No raw content for experience id {}, skipping reclassification", experience.getId());
            return;
        }

        try {
            String sanitizedRawContent = sanitizeRawContent(experience.getRawContent());
            ExperienceStructuredSnapshot snapshot = parseStructuredUpload(
                    sanitizedRawContent,
                    experience.getTitle(),
                    experience.getCategory(),
                    experience.getPeriod(),
                    experience.getRole()
            );

            experience.updateStructuredDetails(
                    snapshot.title(),
                    snapshot.category(),
                    snapshot.summary(),
                    writeJsonArray(snapshot.aggregatedTechStack()),
                    writeJsonArray(snapshot.aggregatedResults()),
                    snapshot.origin(),
                    writeJsonArray(snapshot.overallTechStack()),
                    writeJsonArray(snapshot.jobKeywords()),
                    writeJsonArray(snapshot.questionTypes()),
                    snapshot.period(),
                    snapshot.role(),
                    snapshot.organization(),
                    sanitizedRawContent
            );
            experience.replaceFacets(buildFacetEntities(snapshot.facets()));
            experience.replaceUnits(experienceUnitFactory.buildUnits(experience));
            Experience saved = experienceRepository.save(experience);
            reindexExperience(saved);

            log.info("Reclassified experience id {}", experience.getId());
        } catch (Exception e) {
            log.error("Failed to reclassify experience id {}", experience.getId(), e);
        }
    }

    @Transactional(readOnly = true)
    public List<ExperienceResponse> getAllExperiences() {
        return experienceRepository.findAllWithFacets().stream()
                .map(this::buildResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ExperienceResponse reclassifySingle(Long id) {
        Experience experience = experienceRepository.findByIdWithFacets(id)
                .orElseThrow(() -> new IllegalArgumentException("Experience not found: " + id));
        reclassify(experience);
        Experience refreshed = experienceRepository.findByIdWithFacets(id)
                .orElseThrow(() -> new IllegalArgumentException("Experience not found: " + id));
        return buildResponse(refreshed);
    }

    @Transactional
    public ExperienceResponse updateRawContent(Long id, String rawContent) {
        Experience experience = experienceRepository.findByIdWithFacets(id)
                .orElseThrow(() -> new IllegalArgumentException("Experience not found: " + id));

        String normalizedRawContent = sanitizeRawContent(rawContent);
        if (normalizedRawContent.isBlank()) {
            throw new IllegalArgumentException("Markdown content must not be blank.");
        }

        ExperienceStructuredSnapshot snapshot = parseStructuredUpload(
                normalizedRawContent,
                experience.getTitle(),
                experience.getCategory(),
                experience.getPeriod(),
                experience.getRole()
        );

        experience.updateStructuredDetails(
                snapshot.title(),
                snapshot.category(),
                snapshot.summary(),
                writeJsonArray(snapshot.aggregatedTechStack()),
                writeJsonArray(snapshot.aggregatedResults()),
                snapshot.origin(),
                writeJsonArray(snapshot.overallTechStack()),
                writeJsonArray(snapshot.jobKeywords()),
                writeJsonArray(snapshot.questionTypes()),
                snapshot.period(),
                snapshot.role(),
                snapshot.organization(),
                normalizedRawContent
        );
        experience.replaceFacets(buildFacetEntities(snapshot.facets()));
        experience.replaceUnits(experienceUnitFactory.buildUnits(experience));

        Experience saved = experienceRepository.save(experience);
        reindexExperience(saved);

        Experience refreshed = experienceRepository.findByIdWithFacets(id).orElse(saved);
        return buildResponse(refreshed);
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

    @Transactional
    public void reindexAll() {
        resetExperienceIndex();
        List<Experience> allExperiences = experienceRepository.findAllWithFacets();
        for (Experience experience : allExperiences) {
            experience.replaceUnits(experienceUnitFactory.buildUnits(experience));
            Experience saved = experienceRepository.save(experience);
            reindexExperience(saved);
        }
    }

    private void resetExperienceIndex() {
        try {
            IndexOperations indexOperations = elasticsearchOperations.indexOps(ExperienceDocument.class);
            if (indexOperations.exists()) {
                indexOperations.delete();
            }
            indexOperations.createWithMapping();
            log.info("Recreated Elasticsearch index for experience unit documents.");
        } catch (Exception e) {
            log.warn("Failed to recreate experience Elasticsearch index. Continuing with document-level reindex.", e);
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
            throw new IllegalArgumentException("지원되지 않는 파일 형식입니다. (.md, .json만 가능): " + originalFileName);
        }
    }

    private ExperienceStructuredSnapshot parseStructuredUpload(
            String content,
            String fallbackTitle,
            String fallbackCategory,
            String fallbackPeriod,
            String fallbackRole
    ) {
        ExperienceStructuredSnapshot structured = isLikelyJson(content)
                ? parseJsonSnapshot(content)
                : parseMarkdownSnapshot(content);

        if (needsAiAssistance(structured)) {
            try {
                ExperienceExtractionResult aiResult = sanitizeExtractionResult(experienceAiService.extractExperience(content));
                structured = mergeWithAiFallback(structured, toStructuredSnapshot(aiResult));
            } catch (Exception e) {
                log.warn("AI fallback failed for experience upload parsing. Falling back to rule-based result only.", e);
            }
        }

        return finalizeSnapshot(structured, fallbackTitle, fallbackCategory, fallbackPeriod, fallbackRole);
    }

    private boolean isLikelyJson(String content) {
        String trimmed = content == null ? "" : content.trim();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    private boolean needsAiAssistance(ExperienceStructuredSnapshot snapshot) {
        if (snapshot == null) {
            return true;
        }
        if (snapshot.title().isBlank()) {
            return true;
        }
        return snapshot.facets().isEmpty() || snapshot.facets().stream().allMatch(this::isFacetEffectivelyEmpty);
    }

    private boolean isFacetEffectivelyEmpty(FacetSnapshot facet) {
        if (facet == null) {
            return true;
        }

        return sanitizeInlineText(facet.title()).isBlank()
                && facet.situation().isEmpty()
                && facet.role().isEmpty()
                && facet.judgment().isEmpty()
                && facet.actions().isEmpty()
                && facet.results().isEmpty()
                && facet.techStack().isEmpty()
                && facet.jobKeywords().isEmpty()
                && facet.questionTypes().isEmpty();
    }

    private ExperienceStructuredSnapshot mergeWithAiFallback(
            ExperienceStructuredSnapshot structured,
            ExperienceStructuredSnapshot aiSnapshot
    ) {
        if (structured == null) {
            return aiSnapshot;
        }
        if (aiSnapshot == null) {
            return structured;
        }

        List<FacetSnapshot> mergedFacets = structured.facets().isEmpty()
                ? aiSnapshot.facets()
                : structured.facets();

        return new ExperienceStructuredSnapshot(
                firstNonBlank(structured.title(), aiSnapshot.title()),
                firstNonBlank(structured.category(), aiSnapshot.category()),
                firstNonBlank(structured.summary(), aiSnapshot.summary()),
                firstNonBlank(structured.origin(), aiSnapshot.origin()),
                firstNonBlank(structured.organization(), aiSnapshot.organization()),
                firstNonBlank(structured.role(), aiSnapshot.role()),
                firstNonBlank(structured.period(), aiSnapshot.period()),
                firstNonEmpty(structured.overallTechStack(), aiSnapshot.overallTechStack()),
                firstNonEmpty(structured.jobKeywords(), aiSnapshot.jobKeywords()),
                firstNonEmpty(structured.questionTypes(), aiSnapshot.questionTypes()),
                sanitizeFacets(mergedFacets)
        );
    }

    private ExperienceStructuredSnapshot parseJsonSnapshot(String rawContent) {
        try {
            JsonNode root = objectMapper.readTree(rawContent);
            String title = readText(root, "title");
            String category = readText(root, "category");
            String legacyNarrative = readNarrativeText(root);
            String summary = firstNonBlank(
                    readText(root, "summary"),
                    readText(root, "description"),
                    buildNarrativeSummary(legacyNarrative)
            );
            String origin = firstNonBlank(readText(root, "origin"), readText(root, "source"), readText(root, "context"));
            String organization = readText(root, "organization");
            String role = readText(root, "role");
            String period = readText(root, "period");
            List<String> overallTechStack = firstNonEmpty(
                    readStringList(root, "overallTechStack"),
                    readStringList(root, "projectTechStack")
            );
            List<String> jobKeywords = readStringList(root, "jobKeywords");
            List<String> questionTypes = readStringList(root, "questionTypes");

            List<FacetSnapshot> facets = new ArrayList<>();
            JsonNode facetNodes = root.path("facets");
            if (facetNodes.isArray()) {
                int index = 0;
                for (JsonNode facetNode : facetNodes) {
                    FacetSnapshot facet = facetFromJsonNode(facetNode, index++);
                    if (!isFacetEffectivelyEmpty(facet)) {
                        facets.add(facet);
                    }
                }
            }

            if (facets.isEmpty()) {
                FacetSnapshot legacyFacet = new FacetSnapshot(
                        firstNonBlank(readText(root, "facetTitle"), title),
                        readStringList(root, "situation"),
                        buildLegacyRoleList(readStringList(root, "role"), role),
                        readStringList(root, "judgment"),
                        firstNonEmpty(readStringList(root, "actions"), legacyNarrative.isBlank() ? List.of() : List.of(legacyNarrative)),
                        firstNonEmpty(readStringList(root, "results"), readStringList(root, "metrics")),
                        firstNonEmpty(readStringList(root, "techStack"), overallTechStack),
                        jobKeywords,
                        questionTypes
                );
                if (!isFacetEffectivelyEmpty(legacyFacet)) {
                    facets.add(legacyFacet);
                }
            }

            return new ExperienceStructuredSnapshot(
                    title,
                    category,
                    summary,
                    origin,
                    organization,
                    role,
                    period,
                    sanitizeList(overallTechStack),
                    sanitizeList(jobKeywords),
                    sanitizeList(questionTypes),
                    sanitizeFacets(facets)
            );
        } catch (Exception e) {
            log.warn("Failed to parse uploaded JSON experience. Falling back to empty snapshot.", e);
            return ExperienceStructuredSnapshot.empty();
        }
    }

    private String readNarrativeText(JsonNode node) {
        return firstNonBlank(
                readText(node, "content"),
                readText(node, "body"),
                readText(node, "narrative"),
                readText(node, "details")
        );
    }

    private String buildNarrativeSummary(String narrative) {
        String sanitized = sanitizeInlineText(narrative);
        if (sanitized.isBlank()) {
            return "";
        }

        if (sanitized.length() <= 180) {
            return sanitized;
        }

        int sentenceBoundary = sanitized.lastIndexOf(". ", 180);
        if (sentenceBoundary >= 80) {
            return sanitized.substring(0, sentenceBoundary + 1).trim();
        }

        return sanitized.substring(0, 180).trim() + "...";
    }

    private FacetSnapshot facetFromJsonNode(JsonNode facetNode, int index) {
        return new FacetSnapshot(
                firstNonBlank(readText(facetNode, "title"), "Facet " + (index + 1)),
                readStringList(facetNode, "situation"),
                readStringList(facetNode, "role"),
                readStringList(facetNode, "judgment"),
                readStringList(facetNode, "actions"),
                firstNonEmpty(readStringList(facetNode, "results"), readStringList(facetNode, "metrics")),
                readStringList(facetNode, "techStack"),
                readStringList(facetNode, "jobKeywords"),
                readStringList(facetNode, "questionTypes")
        );
    }

    private ExperienceStructuredSnapshot parseMarkdownSnapshot(String rawContent) {
        String[] lines = rawContent.replace("\r\n", "\n").split("\n", -1);
        String title = "";
        Map<String, StringBuilder> projectSections = new LinkedHashMap<>();
        List<FacetBlock> facetBlocks = new ArrayList<>();

        String currentProjectSection = null;
        FacetBlock currentFacet = null;
        String currentFacetSection = null;

        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine;
            String trimmed = line.trim();

            if (trimmed.startsWith("# ") && title.isBlank()) {
                title = trimmed.substring(2).trim();
                continue;
            }

            if (trimmed.startsWith("## ")) {
                String heading = trimmed.substring(3).trim();
                Matcher facetMatcher = FACET_HEADING_PATTERN.matcher(heading);
                if (facetMatcher.matches()) {
                    if (currentFacet != null) {
                        facetBlocks.add(currentFacet);
                    }
                    String facetTitle = sanitizeInlineText(facetMatcher.group(1));
                    currentFacet = new FacetBlock(facetTitle.isBlank() ? "Facet " + (facetBlocks.size() + 1) : facetTitle);
                    currentFacetSection = null;
                    currentProjectSection = null;
                    continue;
                }

                if (currentFacet == null) {
                    currentProjectSection = normalizeHeading(heading);
                    projectSections.putIfAbsent(currentProjectSection, new StringBuilder());
                }
                continue;
            }

            if (trimmed.startsWith("### ") && currentFacet != null) {
                currentFacetSection = normalizeHeading(trimmed.substring(4).trim());
                currentFacet.sections().putIfAbsent(currentFacetSection, new StringBuilder());
                continue;
            }

            if (currentFacet != null && currentFacetSection != null) {
                appendSectionLine(currentFacet.sections().get(currentFacetSection), line);
                continue;
            }

            if (currentProjectSection != null) {
                appendSectionLine(projectSections.get(currentProjectSection), line);
            }
        }

        if (currentFacet != null) {
            facetBlocks.add(currentFacet);
        }

        Map<String, String> metadata = extractLabeledValues(extractList(projectSections, "프로젝트메타데이터", "projectmetadata"));
        List<String> originContext = extractList(projectSections, "출처맥락", "출처", "맥락", "origin");

        String summary = extractParagraph(projectSections, "한줄요약", "summary", "프로젝트개요", "개요");
        String category = firstNonBlank(
                metadata.get("category"),
                extractSingleLine(projectSections, "category", "카테고리")
        );
        String origin = firstNonBlank(
                metadata.get("출처"),
                metadata.get("origin"),
                extractSingleLine(projectSections, "출처맥락", "출처", "origin"),
                originContext.isEmpty() ? "" : originContext.get(0)
        );
        String organization = firstNonBlank(
                metadata.get("조직소속"),
                metadata.get("organization"),
                metadata.get("organizationcontext"),
                extractSingleLine(projectSections, "조직소속", "organization")
        );
        String role = firstNonBlank(
                metadata.get("역할"),
                metadata.get("role"),
                extractSingleLine(projectSections, "프로젝트역할", "역할", "role"),
                originContext.size() > 1 ? originContext.get(1) : ""
        );
        String period = firstNonBlank(
                metadata.get("기간"),
                metadata.get("period"),
                extractSingleLine(projectSections, "프로젝트기간", "기간", "period")
        );

        List<String> overallTechStack = extractList(projectSections,
                "프로젝트전체기술스택", "overalltechstack", "projecttechstack");
        List<String> projectJobKeywords = extractList(projectSections,
                "대표직무연결키워드", "직무연결키워드", "jobkeywords");
        List<String> projectQuestionTypes = extractList(projectSections,
                "대표활용가능한문항유형", "대표활용가능문항유형", "활용가능한문항유형", "활용가능문항유형", "questiontypes");

        List<FacetSnapshot> facets = facetBlocks.stream()
                .map(this::facetFromMarkdownBlock)
                .filter(facet -> !isFacetEffectivelyEmpty(facet))
                .collect(Collectors.toCollection(ArrayList::new));

        if (facets.isEmpty()) {
            FacetSnapshot legacyFacet = new FacetSnapshot(
                    firstNonBlank(title, "핵심 경험"),
                    extractList(projectSections, "문제상황", "상황", "situation"),
                    extractList(projectSections, "내가맡은역할", "맡은역할", "역할", "role"),
                    extractList(projectSections, "내가한판단", "판단", "judgment"),
                    extractList(projectSections, "내가실제로한행동", "실제로한행동", "행동", "actions"),
                    firstNonEmpty(
                            extractList(projectSections, "결과", "성과", "results"),
                            extractList(projectSections, "주요성과", "metrics")
                    ),
                    firstNonEmpty(
                            extractList(projectSections, "기술스택", "techstack"),
                            overallTechStack
                    ),
                    projectJobKeywords,
                    projectQuestionTypes
            );
            if (!isFacetEffectivelyEmpty(legacyFacet)) {
                facets.add(legacyFacet);
            }
        }

        return new ExperienceStructuredSnapshot(
                sanitizeInlineText(title),
                sanitizeInlineText(category),
                sanitizeInlineText(summary),
                sanitizeInlineText(origin),
                sanitizeInlineText(organization),
                sanitizeInlineText(role),
                sanitizeInlineText(period),
                sanitizeList(overallTechStack),
                sanitizeList(projectJobKeywords),
                sanitizeList(projectQuestionTypes),
                sanitizeFacets(facets)
        );
    }

    private FacetSnapshot facetFromMarkdownBlock(FacetBlock facetBlock) {
        Map<String, StringBuilder> sections = facetBlock.sections();
        return new FacetSnapshot(
                sanitizeInlineText(facetBlock.title()),
                extractList(sections, "문제상황", "상황", "situation"),
                extractList(sections, "내가맡은역할", "맡은역할", "role"),
                extractList(sections, "내가한판단", "판단", "judgment"),
                extractList(sections, "내가실제로한행동", "실제로한행동", "행동", "actions"),
                extractList(sections, "결과", "성과", "results"),
                extractList(sections, "기술스택", "techstack"),
                extractList(sections, "직무연결키워드", "jobkeywords"),
                extractList(sections, "활용가능한문항유형", "활용가능문항유형", "questiontypes")
        );
    }

    private ExperienceStructuredSnapshot finalizeSnapshot(
            ExperienceStructuredSnapshot raw,
            String fallbackTitle,
            String fallbackCategory,
            String fallbackPeriod,
            String fallbackRole
    ) {
        ExperienceStructuredSnapshot base = raw == null ? ExperienceStructuredSnapshot.empty() : raw;

        List<FacetSnapshot> facets = sanitizeFacets(base.facets()).stream()
                .filter(facet -> !isFacetEffectivelyEmpty(facet))
                .collect(Collectors.toCollection(ArrayList::new));

        if (facets.isEmpty()) {
            facets.add(new FacetSnapshot(
                    firstNonBlank(base.title(), fallbackTitle, "핵심 경험"),
                    List.of(),
                    buildLegacyRoleList(List.of(), firstNonBlank(base.role(), fallbackRole)),
                    List.of(),
                    List.of(),
                    List.of(),
                    firstNonEmpty(base.overallTechStack(), List.of()),
                    firstNonEmpty(base.jobKeywords(), List.of()),
                    firstNonEmpty(base.questionTypes(), List.of())
            ));
        }

        List<String> facetTechStack = aggregateFacetValues(facets, FacetSnapshot::techStack);
        List<String> facetResults = aggregateFacetValues(facets, FacetSnapshot::results);
        List<String> facetJobKeywords = aggregateFacetValues(facets, FacetSnapshot::jobKeywords);
        List<String> facetQuestionTypes = aggregateFacetValues(facets, FacetSnapshot::questionTypes);

        String summary = firstNonBlank(base.summary(), buildSummaryFallback(facets), fallbackTitle);
        List<String> overallTechStack = firstNonEmpty(base.overallTechStack(), facetTechStack);
        List<String> projectJobKeywords = firstNonEmpty(base.jobKeywords(), facetJobKeywords);
        List<String> projectQuestionTypes = firstNonEmpty(base.questionTypes(), facetQuestionTypes);

        return new ExperienceStructuredSnapshot(
                firstNonBlank(base.title(), fallbackTitle, "Untitled Experience"),
                firstNonBlank(base.category(), fallbackCategory),
                summary,
                sanitizeInlineText(base.origin()),
                sanitizeInlineText(base.organization()),
                firstNonBlank(base.role(), fallbackRole),
                firstNonBlank(base.period(), fallbackPeriod),
                sanitizeList(overallTechStack),
                sanitizeList(projectJobKeywords),
                sanitizeList(projectQuestionTypes),
                facets.stream()
                        .map(facet -> normalizeFacet(facet, base.title()))
                        .collect(Collectors.toList())
        );
    }

    private FacetSnapshot normalizeFacet(FacetSnapshot facet, String projectTitle) {
        return new FacetSnapshot(
                firstNonBlank(facet.title(), projectTitle, "핵심 facet"),
                sanitizeList(facet.situation()),
                sanitizeList(facet.role()),
                sanitizeList(facet.judgment()),
                sanitizeList(facet.actions()),
                sanitizeList(facet.results()),
                sanitizeList(facet.techStack()),
                sanitizeList(facet.jobKeywords()),
                sanitizeList(facet.questionTypes())
        );
    }

    private String buildSummaryFallback(List<FacetSnapshot> facets) {
        for (FacetSnapshot facet : facets) {
            String candidate = firstNonBlank(
                    facet.title(),
                    facet.results().isEmpty() ? "" : facet.results().get(0),
                    facet.actions().isEmpty() ? "" : facet.actions().get(0),
                    facet.situation().isEmpty() ? "" : facet.situation().get(0)
            );
            if (!candidate.isBlank()) {
                return candidate;
            }
        }
        return "";
    }

    private List<ExperienceFacet> buildFacetEntities(List<FacetSnapshot> facets) {
        List<ExperienceFacet> entities = new ArrayList<>();
        for (int i = 0; i < facets.size(); i++) {
            FacetSnapshot facet = facets.get(i);
            entities.add(ExperienceFacet.builder()
                    .title(firstNonBlank(facet.title(), "Facet " + (i + 1)))
                    .displayOrder(i)
                    .situation(writeJsonArray(facet.situation()))
                    .role(writeJsonArray(facet.role()))
                    .judgment(writeJsonArray(facet.judgment()))
                    .actions(writeJsonArray(facet.actions()))
                    .results(writeJsonArray(facet.results()))
                    .techStack(writeJsonArray(facet.techStack()))
                    .jobKeywords(writeJsonArray(facet.jobKeywords()))
                    .questionTypes(writeJsonArray(facet.questionTypes()))
                    .build());
        }
        return entities;
    }

    private void reindexExperience(Experience experience) {
        experienceRepository.flush();
        try {
            experienceDocumentRepository.deleteByExperienceId(experience.getId());
        } catch (Exception e) {
            log.warn("Failed to clear previous experience documents before reindex. experienceId={}", experience.getId(), e);
        }
        indexToElasticsearch(experience);
    }

    private void indexToElasticsearch(Experience experience) {
        if ("demo".equals(openAiApiKey)) {
            log.warn("OpenAI API Key not set. Skipping Elasticsearch embedding to save costs/prevent errors.");
            return;
        }

        List<IndexSource> indexSources = buildIndexSources(experience);
        if (indexSources.isEmpty()) {
            return;
        }

        EmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
                .apiKey(openAiApiKey)
                .modelName("text-embedding-3-small")
                .build();

        List<ExperienceDocument> esDocuments = new ArrayList<>();
        for (IndexSource source : indexSources) {
            String text = source.content();
            Embedding embedding = embeddingModel.embed(text).content();

            esDocuments.add(ExperienceDocument.builder()
                    .id(source.documentId())
                    .experienceId(experience.getId())
                    .facetId(source.facetId())
                    .unitId(source.unitId())
                    .facetTitle(source.facetTitle())
                    .unitType(source.unitType())
                    .intentTags(source.intentTags())
                    .techStack(source.techStack())
                    .jobKeywords(source.jobKeywords())
                    .questionTypes(source.questionTypes())
                    .chunkText(text)
                    .embedding(embedding.vector())
                    .build());
        }

        experienceDocumentRepository.saveAll(esDocuments);
        log.info("Indexed {} units for experience id {}", esDocuments.size(), experience.getId());
    }

    private List<IndexSource> buildIndexSources(Experience experience) {
        List<ExperienceUnit> units = experience.getUnits();
        if (units == null || units.isEmpty()) {
            return List.of();
        }

        return units.stream()
                .filter(unit -> unit != null && unit.getId() != null)
                .map(unit -> new IndexSource(
                        "unit-" + unit.getId(),
                        unit.getFacet() == null ? null : unit.getFacet().getId(),
                        unit.getFacet() == null ? "" : sanitizeInlineText(unit.getFacet().getTitle()),
                        unit.getId(),
                        unit.getUnitType().name(),
                        readJsonArray(unit.getIntentTags()),
                        readJsonArray(unit.getTechStack()),
                        readJsonArray(unit.getJobKeywords()),
                        readJsonArray(unit.getQuestionTypes()),
                        buildUnitNarrative(experience, unit)
                ))
                .collect(Collectors.toList());
    }

    private List<IndexSource> buildIndexSourcesLegacy(Experience experience) {
        List<String> overallTechStack = readJsonArray(experience.getOverallTechStack());
        List<String> jobKeywords = readJsonArray(experience.getJobKeywords());
        List<String> questionTypes = readJsonArray(experience.getQuestionTypes());
        String rawContent = sanitizeRawContent(experience.getRawContent());

        if (experience.getFacets() == null || experience.getFacets().isEmpty()) {
            return List.of(new IndexSource(
                    null,
                    sanitizeInlineText(experience.getTitle()),
                    buildProjectNarrative(experience, overallTechStack, jobKeywords, questionTypes, rawContent)
            ));
        }

        return experience.getFacets().stream()
                .map(facet -> new IndexSource(
                        facet.getId(),
                        sanitizeInlineText(facet.getTitle()),
                        buildFacetNarrative(experience, facet, overallTechStack, jobKeywords, questionTypes, rawContent)
                ))
                .collect(Collectors.toList());
    }

    private String buildUnitNarrative(Experience experience, ExperienceUnit unit) {
        StringBuilder sb = new StringBuilder();
        appendIfPresent(sb, "Project", experience.getTitle());
        appendIfPresent(sb, "Summary", experience.getDescription());
        appendIfPresent(sb, "Role", experience.getRole());
        appendIfPresent(sb, "Facet", unit.getFacet() == null ? "" : unit.getFacet().getTitle());
        appendIfPresent(sb, "Unit type", unit.getUnitType().name());
        appendIfPresent(sb, "Unit detail", unit.getText());
        appendIfPresent(sb, "Tech stack", joinList(readJsonArray(unit.getTechStack())));
        appendIfPresent(sb, "Job keywords", joinList(readJsonArray(unit.getJobKeywords())));
        appendIfPresent(sb, "Question types", joinList(readJsonArray(unit.getQuestionTypes())));
        appendIfPresent(sb, "Intent tags", joinList(readJsonArray(unit.getIntentTags())));
        return sb.toString();
    }

    private String buildProjectNarrative(
            Experience experience,
            List<String> overallTechStack,
            List<String> jobKeywords,
            List<String> questionTypes,
            String rawContent
    ) {
        StringBuilder sb = new StringBuilder();
        appendIfPresent(sb, "프로젝트", experience.getTitle());
        appendIfPresent(sb, "한 줄 요약", experience.getDescription());
        appendIfPresent(sb, "출처/맥락", experience.getOrigin());
        appendIfPresent(sb, "조직/소속", experience.getOrganization());
        appendIfPresent(sb, "프로젝트 역할", experience.getRole());
        appendIfPresent(sb, "기간", experience.getPeriod());
        appendIfPresent(sb, "프로젝트 전체 기술 스택", joinList(overallTechStack));
        appendIfPresent(sb, "대표 직무 연결 키워드", joinList(jobKeywords));
        appendIfPresent(sb, "대표 활용 가능한 문항 유형", joinList(questionTypes));
        appendIfPresent(sb, "프로젝트 결과/성과", joinList(readJsonArray(experience.getMetrics())));
        appendIfPresent(sb, "업로드 원문", rawContent);
        return sb.toString();
    }

    private String buildFacetNarrative(
            Experience experience,
            ExperienceFacet facet,
            List<String> overallTechStack,
            List<String> jobKeywords,
            List<String> questionTypes,
            String rawContent
    ) {
        StringBuilder sb = new StringBuilder(buildProjectNarrative(experience, overallTechStack, jobKeywords, questionTypes, ""));
        appendIfPresent(sb, "Facet 제목", facet.getTitle());
        appendIfPresent(sb, "Facet 문제 상황", joinList(readJsonArray(facet.getSituation())));
        appendIfPresent(sb, "Facet 역할", joinList(readJsonArray(facet.getRole())));
        appendIfPresent(sb, "Facet 판단", joinList(readJsonArray(facet.getJudgment())));
        appendIfPresent(sb, "Facet 행동", joinList(readJsonArray(facet.getActions())));
        appendIfPresent(sb, "Facet 결과", joinList(readJsonArray(facet.getResults())));
        appendIfPresent(sb, "Facet 기술 스택", joinList(readJsonArray(facet.getTechStack())));
        appendIfPresent(sb, "Facet 직무 연결 키워드", joinList(readJsonArray(facet.getJobKeywords())));
        appendIfPresent(sb, "Facet 활용 가능한 문항 유형", joinList(readJsonArray(facet.getQuestionTypes())));
        appendIfPresent(sb, "업로드 원문", rawContent);
        return sb.toString();
    }

    private void appendIfPresent(StringBuilder builder, String label, String value) {
        String sanitized = sanitizeInlineText(value);
        if (sanitized.isBlank()) {
            return;
        }

        if (!builder.isEmpty()) {
            builder.append('\n');
        }
        builder.append(label).append(": ").append(sanitized);
    }

    private ExperienceStructuredSnapshot toStructuredSnapshot(ExperienceExtractionResult result) {
        if (result == null) {
            return ExperienceStructuredSnapshot.empty();
        }

        List<FacetSnapshot> facets = new ArrayList<>();
        if (result.getFacets() != null) {
            for (ExperienceExtractionResult.Facet facet : result.getFacets()) {
                facets.add(new FacetSnapshot(
                        sanitizeInlineText(facet.getTitle()),
                        sanitizeList(facet.getSituation()),
                        sanitizeList(facet.getRole()),
                        sanitizeList(facet.getJudgment()),
                        sanitizeList(facet.getActions()),
                        sanitizeList(facet.getResults()),
                        sanitizeList(facet.getTechStack()),
                        sanitizeList(facet.getJobKeywords()),
                        sanitizeList(facet.getQuestionTypes())
                ));
            }
        }

        if (facets.isEmpty()) {
            FacetSnapshot legacyFacet = new FacetSnapshot(
                    firstNonBlank(result.getTitle(), "핵심 경험"),
                    List.of(),
                    buildLegacyRoleList(List.of(), result.getRole()),
                    List.of(),
                    List.of(),
                    sanitizeList(result.getMetrics()),
                    firstNonEmpty(sanitizeList(result.getTechStack()), sanitizeList(result.getOverallTechStack())),
                    sanitizeList(result.getJobKeywords()),
                    sanitizeList(result.getQuestionTypes())
            );
            if (!isFacetEffectivelyEmpty(legacyFacet)) {
                facets.add(legacyFacet);
            }
        }

        return new ExperienceStructuredSnapshot(
                sanitizeInlineText(result.getTitle()),
                sanitizeInlineText(result.getCategory()),
                sanitizeInlineText(result.getDescription()),
                sanitizeInlineText(result.getOrigin()),
                sanitizeInlineText(result.getOrganization()),
                sanitizeInlineText(result.getRole()),
                sanitizeInlineText(result.getPeriod()),
                firstNonEmpty(sanitizeList(result.getOverallTechStack()), sanitizeList(result.getTechStack())),
                sanitizeList(result.getJobKeywords()),
                sanitizeList(result.getQuestionTypes()),
                sanitizeFacets(facets)
        );
    }

    private ExperienceExtractionResult sanitizeExtractionResult(ExperienceExtractionResult result) {
        if (result == null) {
            return ExperienceExtractionResult.builder()
                    .techStack(List.of())
                    .metrics(List.of())
                    .overallTechStack(List.of())
                    .jobKeywords(List.of())
                    .questionTypes(List.of())
                    .facets(List.of())
                    .build();
        }

        List<ExperienceExtractionResult.Facet> sanitizedFacets = new ArrayList<>();
        if (result.getFacets() != null) {
            for (ExperienceExtractionResult.Facet facet : result.getFacets()) {
                sanitizedFacets.add(ExperienceExtractionResult.Facet.builder()
                        .title(sanitizeInlineText(facet.getTitle()))
                        .situation(sanitizeList(facet.getSituation()))
                        .role(sanitizeList(facet.getRole()))
                        .judgment(sanitizeList(facet.getJudgment()))
                        .actions(sanitizeList(facet.getActions()))
                        .results(sanitizeList(facet.getResults()))
                        .techStack(sanitizeList(facet.getTechStack()))
                        .jobKeywords(sanitizeList(facet.getJobKeywords()))
                        .questionTypes(sanitizeList(facet.getQuestionTypes()))
                        .build());
            }
        }

        return ExperienceExtractionResult.builder()
                .title(sanitizeInlineText(result.getTitle()))
                .category(sanitizeInlineText(result.getCategory()))
                .description(sanitizeInlineText(result.getDescription()))
                .origin(sanitizeInlineText(result.getOrigin()))
                .techStack(sanitizeList(result.getTechStack()))
                .metrics(sanitizeList(result.getMetrics()))
                .overallTechStack(sanitizeList(result.getOverallTechStack()))
                .jobKeywords(sanitizeList(result.getJobKeywords()))
                .questionTypes(sanitizeList(result.getQuestionTypes()))
                .period(sanitizeInlineText(result.getPeriod()))
                .role(sanitizeInlineText(result.getRole()))
                .organization(sanitizeInlineText(result.getOrganization()))
                .facets(sanitizedFacets)
                .build();
    }

    private ExperienceResponse buildResponse(Experience experience) {
        List<String> techStack = readJsonArray(experience.getTechStack());
        List<String> metrics = readJsonArray(experience.getMetrics());
        List<String> overallTechStack = readJsonArray(experience.getOverallTechStack());
        List<String> jobKeywords = readJsonArray(experience.getJobKeywords());
        List<String> questionTypes = readJsonArray(experience.getQuestionTypes());
        List<ExperienceFacetResponse> facets = experience.getFacets() == null
                ? List.of()
                : experience.getFacets().stream()
                .map(this::buildFacetResponse)
                .collect(Collectors.toList());

        return ExperienceResponse.builder()
                .id(experience.getId())
                .title(sanitizeInlineText(experience.getTitle()))
                .category(sanitizeInlineText(experience.getCategory()))
                .description(sanitizeInlineText(experience.getDescription()))
                .origin(sanitizeInlineText(experience.getOrigin()))
                .techStack(techStack)
                .metrics(metrics)
                .overallTechStack(overallTechStack)
                .jobKeywords(jobKeywords)
                .questionTypes(questionTypes)
                .period(sanitizeInlineText(experience.getPeriod()))
                .role(sanitizeInlineText(experience.getRole()))
                .organization(sanitizeInlineText(experience.getOrganization()))
                .rawContent(sanitizeRawContent(experience.getRawContent()))
                .facets(facets)
                .build();
    }

    private ExperienceFacetResponse buildFacetResponse(ExperienceFacet facet) {
        return ExperienceFacetResponse.builder()
                .id(facet.getId())
                .displayOrder(facet.getDisplayOrder())
                .title(sanitizeInlineText(facet.getTitle()))
                .situation(readJsonArray(facet.getSituation()))
                .role(readJsonArray(facet.getRole()))
                .judgment(readJsonArray(facet.getJudgment()))
                .actions(readJsonArray(facet.getActions()))
                .results(readJsonArray(facet.getResults()))
                .techStack(readJsonArray(facet.getTechStack()))
                .jobKeywords(readJsonArray(facet.getJobKeywords()))
                .questionTypes(readJsonArray(facet.getQuestionTypes()))
                .build();
    }

    private Map<String, String> extractLabeledValues(List<String> lines) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : lines) {
            String cleaned = sanitizeInlineText(line).replaceFirst("^[-*]\\s*", "");
            if (cleaned.isBlank()) {
                continue;
            }

            int separatorIndex = cleaned.indexOf(':');
            if (separatorIndex < 0) {
                separatorIndex = cleaned.indexOf('：');
            }
            if (separatorIndex < 0) {
                continue;
            }

            String key = normalizeHeading(cleaned.substring(0, separatorIndex));
            String value = sanitizeInlineText(cleaned.substring(separatorIndex + 1));
            if (!key.isBlank() && !value.isBlank()) {
                values.putIfAbsent(key, value);
            }
        }
        return values;
    }

    private void appendSectionLine(StringBuilder builder, String line) {
        if (builder == null) {
            return;
        }
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(line);
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
        return extractListFromText(text);
    }

    private String getSectionText(Map<String, StringBuilder> sections, String key) {
        StringBuilder builder = sections.get(key);
        return builder == null ? "" : builder.toString().trim();
    }

    private List<String> readStringList(JsonNode node, String... fieldNames) {
        if (node == null || fieldNames == null) {
            return List.of();
        }

        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            List<String> extracted = stringListFromNode(value);
            if (!extracted.isEmpty()) {
                return extracted;
            }
        }
        return List.of();
    }

    private String readText(JsonNode node, String... fieldNames) {
        if (node == null || fieldNames == null) {
            return "";
        }

        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            String extracted = textFromNode(value);
            if (!extracted.isBlank()) {
                return extracted;
            }
        }
        return "";
    }

    private String textFromNode(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return "";
        }
        if (node.isTextual() || node.isNumber() || node.isBoolean()) {
            return sanitizeInlineText(node.asText(""));
        }
        if (node.isArray()) {
            return sanitizeInlineText(stringListFromNode(node).stream().findFirst().orElse(""));
        }
        return "";
    }

    private List<String> stringListFromNode(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return List.of();
        }

        if (node.isArray()) {
            List<String> values = new ArrayList<>();
            node.forEach(child -> {
                if (child.isTextual() || child.isNumber() || child.isBoolean()) {
                    values.add(child.asText(""));
                }
            });
            return sanitizeList(values);
        }

        if (node.isTextual() || node.isNumber() || node.isBoolean()) {
            return extractListFromText(node.asText(""));
        }

        return List.of();
    }

    private List<String> extractListFromText(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        return sanitizeList(text.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .map(line -> line.replaceFirst("^[-*]\\s*", "").trim())
                .flatMap(line -> splitInlineList(line).stream())
                .collect(Collectors.toList()));
    }

    private List<String> splitInlineList(String text) {
        String trimmed = sanitizeInlineText(text);
        if (trimmed.isBlank()) {
            return List.of();
        }
        if (trimmed.contains(", ")) {
            return List.of(trimmed.split("\\s*,\\s*"));
        }
        return List.of(trimmed);
    }

    private List<String> sanitizeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }

        Set<String> uniqueValues = new LinkedHashSet<>();
        for (String value : values) {
            String sanitized = sanitizeInlineText(value).replaceFirst("^[-*]\\s*", "").trim();
            if (!sanitized.isBlank()) {
                uniqueValues.add(sanitized);
            }
        }

        return List.copyOf(uniqueValues);
    }

    private List<FacetSnapshot> sanitizeFacets(List<FacetSnapshot> facets) {
        if (facets == null || facets.isEmpty()) {
            return List.of();
        }

        List<FacetSnapshot> sanitized = new ArrayList<>();
        for (FacetSnapshot facet : facets) {
            if (facet == null) {
                continue;
            }
            sanitized.add(new FacetSnapshot(
                    sanitizeInlineText(facet.title()),
                    sanitizeList(facet.situation()),
                    sanitizeList(facet.role()),
                    sanitizeList(facet.judgment()),
                    sanitizeList(facet.actions()),
                    sanitizeList(facet.results()),
                    sanitizeList(facet.techStack()),
                    sanitizeList(facet.jobKeywords()),
                    sanitizeList(facet.questionTypes())
            ));
        }
        return List.copyOf(sanitized);
    }

    private String sanitizeInlineText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        return MULTI_SPACE_PATTERN.matcher(stripCitationArtifacts(value).replace('\n', ' '))
                .replaceAll(" ")
                .trim();
    }

    private String sanitizeRawContent(String value) {
        if (value == null) {
            return "";
        }

        return stripCitationArtifacts(value)
                .replace("\r\n", "\n")
                .replaceAll("[ \t]+\n", "\n")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }

    private String stripCitationArtifacts(String value) {
        String withoutFileCitations = FILE_CITATION_PATTERN.matcher(value).replaceAll(" ");
        String withoutBracketCitations = BRACKET_CITATION_PATTERN.matcher(withoutFileCitations).replaceAll(" ");
        return ZERO_WIDTH_PATTERN.matcher(withoutBracketCitations).replaceAll("");
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            String sanitized = sanitizeInlineText(value);
            if (!sanitized.isBlank()) {
                return sanitized;
            }
        }
        return "";
    }

    @SafeVarargs
    private final List<String> firstNonEmpty(List<String>... candidates) {
        if (candidates == null) {
            return List.of();
        }

        for (List<String> candidate : candidates) {
            List<String> sanitized = sanitizeList(candidate);
            if (!sanitized.isEmpty()) {
                return sanitized;
            }
        }
        return List.of();
    }

    private List<String> buildLegacyRoleList(List<String> roleLines, String roleValue) {
        List<String> sanitized = sanitizeList(roleLines);
        if (!sanitized.isEmpty()) {
            return sanitized;
        }

        String role = sanitizeInlineText(roleValue);
        return role.isBlank() ? List.of() : List.of(role);
    }

    private List<String> aggregateFacetValues(
            List<FacetSnapshot> facets,
            java.util.function.Function<FacetSnapshot, List<String>> extractor
    ) {
        if (facets == null || facets.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (FacetSnapshot facet : facets) {
            if (facet == null) {
                continue;
            }
            values.addAll(sanitizeList(extractor.apply(facet)));
        }
        return List.copyOf(values);
    }

    private String joinList(List<String> values) {
        List<String> sanitized = sanitizeList(values);
        return sanitized.isEmpty() ? "" : String.join(", ", sanitized);
    }

    private String writeJsonArray(List<String> values) {
        try {
            return objectMapper.writeValueAsString(sanitizeList(values));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize experience list field.", e);
        }
    }

    private List<String> readJsonArray(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        try {
            return sanitizeList(objectMapper.readValue(value, new TypeReference<>() {
            }));
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse JSON array from stored experience field", e);
            return List.of();
        }
    }

    private record ExperienceStructuredSnapshot(
            String title,
            String category,
            String summary,
            String origin,
            String organization,
            String role,
            String period,
            List<String> overallTechStack,
            List<String> jobKeywords,
            List<String> questionTypes,
            List<FacetSnapshot> facets
    ) {
        private static ExperienceStructuredSnapshot empty() {
            return new ExperienceStructuredSnapshot(
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of()
            );
        }

        private List<String> aggregatedTechStack() {
            return aggregateFacetValues(facets, FacetSnapshot::techStack);
        }

        private List<String> aggregatedResults() {
            return aggregateFacetValues(facets, FacetSnapshot::results);
        }

        private static List<String> aggregateFacetValues(
                List<FacetSnapshot> facets,
                java.util.function.Function<FacetSnapshot, List<String>> extractor
        ) {
            if (facets == null || facets.isEmpty()) {
                return List.of();
            }

            LinkedHashSet<String> values = new LinkedHashSet<>();
            for (FacetSnapshot facet : facets) {
                if (facet == null) {
                    continue;
                }
                List<String> extracted = extractor.apply(facet);
                if (extracted != null) {
                    values.addAll(extracted.stream()
                            .filter(Objects::nonNull)
                            .map(String::trim)
                            .filter(value -> !value.isBlank())
                            .toList());
                }
            }
            return List.copyOf(values);
        }
    }

    private record FacetSnapshot(
            String title,
            List<String> situation,
            List<String> role,
            List<String> judgment,
            List<String> actions,
            List<String> results,
            List<String> techStack,
            List<String> jobKeywords,
            List<String> questionTypes
    ) {
    }

    private record FacetBlock(String title, Map<String, StringBuilder> sections) {
        private FacetBlock(String title) {
            this(title, new LinkedHashMap<>());
        }
    }

    private record IndexSource(
            String documentId,
            Long facetId,
            String facetTitle,
            Long unitId,
            String unitType,
            List<String> intentTags,
            List<String> techStack,
            List<String> jobKeywords,
            List<String> questionTypes,
            String content
    ) {
        private IndexSource(Long facetId, String facetTitle, String content) {
            this(
                    facetId == null ? "exp-legacy" : "facet-legacy-" + facetId,
                    facetId,
                    facetTitle,
                    null,
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    content
            );
        }
    }
}

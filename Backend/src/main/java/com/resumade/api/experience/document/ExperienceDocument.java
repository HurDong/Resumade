package com.resumade.api.experience.document;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "experience-docs")
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@NoArgsConstructor
public class ExperienceDocument {

    @Id
    private String id;

    @Field(type = FieldType.Long)
    private Long experienceId;

    @Field(type = FieldType.Long)
    private Long facetId;

    @Field(type = FieldType.Long)
    private Long unitId;

    @Field(type = FieldType.Keyword)
    private String facetTitle;

    @Field(type = FieldType.Keyword)
    private String unitType;

    @Field(type = FieldType.Keyword)
    private java.util.List<String> intentTags;

    @Field(type = FieldType.Keyword)
    private java.util.List<String> techStack;

    @Field(type = FieldType.Keyword)
    private java.util.List<String> jobKeywords;

    @Field(type = FieldType.Keyword)
    private java.util.List<String> questionTypes;

    @Field(type = FieldType.Text, analyzer = "standard") 
    private String chunkText;

    @Field(type = FieldType.Dense_Vector, dims = 1536) // 1536 is for text-embedding-3-small
    private float[] embedding;

    @Builder
    public ExperienceDocument(
            String id,
            Long experienceId,
            Long facetId,
            Long unitId,
            String facetTitle,
            String unitType,
            java.util.List<String> intentTags,
            java.util.List<String> techStack,
            java.util.List<String> jobKeywords,
            java.util.List<String> questionTypes,
            String chunkText,
            float[] embedding
    ) {
        this.id = id;
        this.experienceId = experienceId;
        this.facetId = facetId;
        this.unitId = unitId;
        this.facetTitle = facetTitle;
        this.unitType = unitType;
        this.intentTags = intentTags;
        this.techStack = techStack;
        this.jobKeywords = jobKeywords;
        this.questionTypes = questionTypes;
        this.chunkText = chunkText;
        this.embedding = embedding;
    }
}


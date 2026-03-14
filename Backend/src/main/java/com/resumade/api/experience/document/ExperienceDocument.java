package com.resumade.api.experience.document;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "experience-docs")
@Getter
@Setter
@NoArgsConstructor
public class ExperienceDocument {

    @Id
    private String id;

    @Field(type = FieldType.Long)
    private Long experienceId;

    @Field(type = FieldType.Text, analyzer = "standard") 
    private String chunkText;

    @Field(type = FieldType.Dense_Vector, dims = 1536) // 1536 is for text-embedding-3-small
    private float[] embedding;

    @Builder
    public ExperienceDocument(Long experienceId, String chunkText, float[] embedding) {
        this.experienceId = experienceId;
        this.chunkText = chunkText;
        this.embedding = embedding;
    }
}


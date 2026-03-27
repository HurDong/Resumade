package com.resumade.api.profile.dto;

import com.resumade.api.profile.domain.ProfileEntry;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProfileEntryResponse {

    private String id;
    private String category;
    private String title;
    private String organization;
    private String dateLabel;
    private String summary;
    private String referenceId;
    private String highlight;
    private int sortOrder;

    public static ProfileEntryResponse from(ProfileEntry entry) {
        return ProfileEntryResponse.builder()
                .id(entry.getId())
                .category(entry.getCategory().getValue())
                .title(entry.getTitle())
                .organization(entry.getOrganization())
                .dateLabel(entry.getDateLabel())
                .summary(entry.getSummary())
                .referenceId(entry.getReferenceId())
                .highlight(entry.getHighlight())
                .sortOrder(entry.getSortOrder())
                .build();
    }
}

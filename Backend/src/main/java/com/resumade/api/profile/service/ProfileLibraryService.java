package com.resumade.api.profile.service;

import com.resumade.api.profile.domain.ProfileCategory;
import com.resumade.api.profile.domain.ProfileEntry;
import com.resumade.api.profile.domain.ProfileEntryRepository;
import com.resumade.api.profile.dto.ProfileEntryResponse;
import com.resumade.api.profile.dto.ProfileEntryUpsertRequest;
import com.resumade.api.profile.dto.ReorderRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileLibraryService {

    private final ProfileEntryRepository profileEntryRepository;

    public List<ProfileEntryResponse> getAllEntries() {
        return profileEntryRepository.findAllByOrderBySortOrderAscCreatedAtAsc().stream()
                .map(ProfileEntryResponse::from)
                .toList();
    }

    @Transactional
    public ProfileEntryResponse createEntry(ProfileEntryUpsertRequest request) {
        ProfileCategory category = resolveCategory(request.category());
        int nextSortOrder = profileEntryRepository.findMaxSortOrderByCategory(category) + 1;

        ProfileEntry entry = ProfileEntry.builder()
                .id(trimToNull(request.id()))
                .category(category)
                .title(normalize(request.title()))
                .organization(normalize(request.organization()))
                .dateLabel(normalize(request.dateLabel()))
                .summary(normalize(request.summary()))
                .referenceId(normalize(request.referenceId()))
                .highlight(normalize(request.highlight()))
                .sortOrder(nextSortOrder)
                .build();

        return ProfileEntryResponse.from(profileEntryRepository.save(entry));
    }

    @Transactional
    public void reorderEntries(ReorderRequest request) {
        List<String> ids = request.ids();
        Map<String, ProfileEntry> entryMap = profileEntryRepository.findAllById(ids)
                .stream()
                .collect(Collectors.toMap(ProfileEntry::getId, Function.identity()));

        for (int i = 0; i < ids.size(); i++) {
            ProfileEntry entry = entryMap.get(ids.get(i));
            if (entry != null) {
                entry.updateSortOrder(i);
            }
        }
    }

    @Transactional
    public ProfileEntryResponse updateEntry(String id, ProfileEntryUpsertRequest request) {
        ProfileEntry entry = profileEntryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Profile entry not found: " + id));

        entry.update(
                resolveCategory(request.category()),
                normalize(request.title()),
                normalize(request.organization()),
                normalize(request.dateLabel()),
                normalize(request.summary()),
                normalize(request.referenceId()),
                normalize(request.highlight())
        );

        return ProfileEntryResponse.from(entry);
    }

    @Transactional
    public void deleteEntry(String id) {
        if (!profileEntryRepository.existsById(id)) {
            throw new IllegalArgumentException("Profile entry not found: " + id);
        }

        profileEntryRepository.deleteById(id);
    }

    private ProfileCategory resolveCategory(String value) {
        return ProfileCategory.from(normalize(value));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}

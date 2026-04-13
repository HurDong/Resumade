package com.resumade.api.workspace.service;

import com.resumade.api.workspace.domain.QuestionSnapshot;
import com.resumade.api.workspace.domain.QuestionSnapshotRepository;
import com.resumade.api.workspace.domain.SnapshotType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionSnapshotService {

    private static final int MAX_SNAPSHOTS_PER_QUESTION = 20;

    private final QuestionSnapshotRepository snapshotRepository;

    /**
     * 스냅샷 저장 — 비동기 처리하여 메인 파이프라인을 블록하지 않습니다.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveSnapshot(Long questionId, SnapshotType type, String content) {
        if (content == null || content.isBlank()) return;
        try {
            snapshotRepository.save(
                    QuestionSnapshot.builder()
                            .questionId(questionId)
                            .snapshotType(type)
                            .content(content)
                            .build()
            );
            trimOldSnapshots(questionId);
        } catch (Exception e) {
            log.warn("스냅샷 저장 실패 (questionId={}, type={}): {}", questionId, type, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<QuestionSnapshot> getHistory(Long questionId) {
        return snapshotRepository.findByQuestionIdOrderByCreatedAtDesc(questionId);
    }

    private void trimOldSnapshots(Long questionId) {
        long count = snapshotRepository.countByQuestionId(questionId);
        if (count > MAX_SNAPSHOTS_PER_QUESTION) {
            snapshotRepository.deleteOldSnapshots(questionId, MAX_SNAPSHOTS_PER_QUESTION);
        }
    }
}

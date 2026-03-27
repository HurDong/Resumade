package com.resumade.api.workspace.domain;

public enum SnapshotType {
    /** AI가 생성한 초안 저장 시점 */
    DRAFT_GENERATED,
    /** DeepL 한→영→한 세탁 완료 시점 */
    WASHED,
    /** 사용자가 최종 에디터에서 저장한 시점 */
    FINAL_EDIT
}

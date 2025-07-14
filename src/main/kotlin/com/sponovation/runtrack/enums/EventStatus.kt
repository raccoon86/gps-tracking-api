package com.sponovation.runtrack.enums

/**
 * 이벤트 상태를 나타내는 열거형
 * 
 * 이벤트 생명주기:
 * - DRAFT: 초안 상태 (아직 공개되지 않음)
 * - SCHEDULED: 예정된 이벤트 (참가 신청 가능)
 * - COMPLETED: 완료된 이벤트
 * - UNCONFIRMED: 확정되지 않은 이벤트
 * - HOLD: 보류 상태 (일시적 중단)
 * - CANCELED: 취소된 이벤트
 */
enum class EventStatus {
    DRAFT,
    SCHEDULED,
    COMPLETED,
    UNCONFIRMED,
    HOLD,
    CANCELED
} 
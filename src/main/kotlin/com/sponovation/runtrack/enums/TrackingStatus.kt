package com.sponovation.runtrack.enums

/**
 * 트래킹 세션 상태
 * 
 * 사용자의 추적 세션 상태를 나타내는 enum입니다.
 */
enum class TrackingStatus {
    /** 시작됨 */
    STARTED,
    
    /** 진행 중 */
    IN_PROGRESS,
    
    /** 일시정지됨 */
    PAUSED,
    
    /** 완료됨 */
    COMPLETED,
    
    /** 중단됨 */
    STOPPED,
    
    /** 실패함 */
    FAILED
} 
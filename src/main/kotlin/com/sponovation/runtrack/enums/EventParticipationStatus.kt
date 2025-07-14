package com.sponovation.runtrack.enums

/**
 * 이벤트 참가 상태를 나타내는 열거형
 * 
 * 참가자의 이벤트 진행 상태를 관리합니다:
 * - REGISTERED: 참가 등록 완료 상태
 * - IN_PROGRESS: 현재 이벤트 진행 중
 * - FINISHED: 이벤트 완주 성공
 * - DNF: Did Not Finish (완주하지 못함)
 * - DNS: Did Not Start (출발하지 않음)
 */
enum class EventParticipationStatus {
    /** 참가 등록 완료 - 이벤트 시작 전 대기 상태 */
    REGISTERED,
    
    /** 진행 중 - 현재 이벤트에 참여하고 있는 상태 */
    IN_PROGRESS,
    
    /** 완주 - 이벤트를 성공적으로 완료한 상태 */
    FINISHED,
    
    /** 완주하지 못함 - 중도 포기 또는 실격 등의 이유로 완주하지 못한 상태 */
    DNF,
    
    /** 출발하지 않음 - 등록했지만 시작하지 않은 상태 */
    DNS
} 
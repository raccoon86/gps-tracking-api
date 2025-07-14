package com.sponovation.runtrack.repository

import com.sponovation.runtrack.domain.EventParticipant
import com.sponovation.runtrack.enums.EventParticipationStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.Optional

/**
 * 대회 참가자 데이터 접근을 위한 Repository 인터페이스
 * 
 * 대회 참가자 관련 CRUD 연산과 커스텀 쿼리를 제공합니다.
 * Spring Data JPA를 사용하여 기본적인 데이터베이스 연산을 자동화하고,
 * 참가자 검색, 순위 조회, 참가 상태 관리를 위한 커스텀 쿼리를 추가 정의합니다.
 */
@Repository
interface EventParticipantRepository : JpaRepository<EventParticipant, Long> {
    
    // === 기본 조회 메서드 ===
    
    /**
     * 사용자 ID와 이벤트 ID로 참가자 정보 조회
     * 
     * @param userId 사용자 ID
     * @param eventId 이벤트 ID
     * @return 참가자 정보 (Optional)
     */
    fun findByUserIdAndEventId(userId: Long, eventId: Long): Optional<EventParticipant>
    
    /**
     * 특정 이벤트의 모든 참가자 조회
     * 
     * @param eventId 이벤트 ID
     * @param pageable 페이징 정보
     * @return 참가자 페이지
     */
    fun findByEventId(eventId: Long, pageable: Pageable): Page<EventParticipant>
    
    /**
     * 특정 사용자의 모든 참가 이력 조회
     * 
     * @param userId 사용자 ID
     * @param pageable 페이징 정보
     * @return 참가 이력 페이지
     */
    fun findByUserId(userId: Long, pageable: Pageable): Page<EventParticipant>
    
    /**
     * 배번으로 참가자 조회
     * 
     * @param eventId 이벤트 ID
     * @param bibNumber 배번
     * @return 참가자 정보 (Optional)
     */
    fun findByEventIdAndBibNumber(eventId: Long, bibNumber: String): Optional<EventParticipant>
    
    // === 상태별 조회 메서드 ===
    
    /**
     * 특정 이벤트의 상태별 참가자 조회
     * 
     * @param eventId 이벤트 ID
     * @param status 참가 상태
     * @param pageable 페이징 정보
     * @return 참가자 페이지
     */
    fun findByEventIdAndParticipationStatus(eventId: Long, status: EventParticipationStatus, pageable: Pageable): Page<EventParticipant>
    
    /**
     * 특정 이벤트의 완주자 조회
     * 
     * @param eventId 이벤트 ID
     * @param pageable 페이징 정보
     * @return 완주자 페이지
     */
    @Query("SELECT ep FROM EventParticipant ep WHERE ep.eventId = :eventId AND ep.participationStatus = 'FINISHED'")
    fun findFinishedParticipantsByEventId(@Param("eventId") eventId: Long, pageable: Pageable): Page<EventParticipant>
    
    /**
     * 특정 이벤트의 진행 중인 참가자 조회
     * 
     * @param eventId 이벤트 ID
     * @param pageable 페이징 정보
     * @return 진행 중인 참가자 페이지
     */
    @Query("SELECT ep FROM EventParticipant ep WHERE ep.eventId = :eventId AND ep.participationStatus = 'IN_PROGRESS'")
    fun findInProgressParticipantsByEventId(@Param("eventId") eventId: Long, pageable: Pageable): Page<EventParticipant>
    
    // === 카테고리별 조회 메서드 ===
    
    /**
     * 특정 이벤트의 카테고리별 참가자 조회
     * 
     * @param eventId 이벤트 ID
     * @param category 카테고리
     * @param pageable 페이징 정보
     * @return 참가자 페이지
     */
    fun findByEventIdAndCategory(eventId: Long, category: String, pageable: Pageable): Page<EventParticipant>
    
    /**
     * 특정 이벤트의 카테고리별 완주자 조회
     * 
     * @param eventId 이벤트 ID
     * @param category 카테고리
     * @param pageable 페이징 정보
     * @return 완주자 페이지
     */
    fun findByEventIdAndCategoryAndParticipationStatus(
        eventId: Long,
        category: String,
        status: EventParticipationStatus,
        pageable: Pageable
    ): Page<EventParticipant>
    
    // === 순위 조회 메서드 ===
    
    /**
     * 특정 이벤트의 완주자를 완주 시간 순으로 조회
     * 
     * @param eventId 이벤트 ID
     * @param pageable 페이징 정보
     * @return 완주자 페이지 (완주 시간 순)
     */
    @Query("SELECT ep FROM EventParticipant ep WHERE ep.eventId = :eventId AND ep.participationStatus = 'FINISHED' AND ep.actualFinishTimeSeconds IS NOT NULL ORDER BY ep.actualFinishTimeSeconds ASC")
    fun findFinishedParticipantsByEventIdOrderByFinishTime(@Param("eventId") eventId: Long, pageable: Pageable): Page<EventParticipant>
    
    /**
     * 특정 이벤트의 카테고리별 완주자를 완주 시간 순으로 조회
     * 
     * @param eventId 이벤트 ID
     * @param category 카테고리
     * @param pageable 페이징 정보
     * @return 카테고리별 완주자 페이지 (완주 시간 순)
     */
    @Query("SELECT ep FROM EventParticipant ep WHERE ep.eventId = :eventId AND ep.category = :category AND ep.participationStatus = 'FINISHED' AND ep.actualFinishTimeSeconds IS NOT NULL ORDER BY ep.actualFinishTimeSeconds ASC")
    fun findFinishedParticipantsByEventIdAndCategoryOrderByFinishTime(
        @Param("eventId") eventId: Long,
        @Param("category") category: String,
        pageable: Pageable
    ): Page<EventParticipant>
    
    // === 배치 조회 메서드 ===
    
    /**
     * 여러 사용자 ID로 특정 이벤트의 참가자 정보 조회
     * topRankers 구현에서 사용
     * 
     * @param userIds 사용자 ID 목록
     * @param eventId 이벤트 ID
     * @return 참가자 정보 목록
     */
    @Query("SELECT ep FROM EventParticipant ep WHERE ep.userId IN :userIds AND ep.eventId = :eventId")
    fun findByUserIdInAndEventId(@Param("userIds") userIds: List<Long>, @Param("eventId") eventId: Long): List<EventParticipant>
    
    /**
     * 사용자 ID 문자열로 특정 이벤트의 참가자 정보 조회
     * Redis에서 조회한 문자열 ID를 UUID로 변환하여 조회
     * 
     * @param userIdStrings 사용자 ID 문자열 목록
     * @param eventId 이벤트 ID
     * @return 참가자 정보 목록
     */
    @Query("SELECT ep FROM EventParticipant ep WHERE CAST(ep.userId AS string) IN :userIdStrings AND ep.eventId = :eventId")
    fun findByUserIdStringsAndEventId(@Param("userIdStrings") userIdStrings: List<String>, @Param("eventId") eventId: Long): List<EventParticipant>
    
    // === 통계 조회 메서드 ===
    
    /**
     * 특정 이벤트의 참가자 수 조회
     * 
     * @param eventId 이벤트 ID
     * @return 참가자 수
     */
    fun countByEventId(eventId: Long): Long
    
    /**
     * 특정 이벤트의 상태별 참가자 수 조회
     * 
     * @param eventId 이벤트 ID
     * @param status 참가 상태
     * @return 상태별 참가자 수
     */
    fun countByEventIdAndParticipationStatus(eventId: Long, status: EventParticipationStatus): Long
    
    /**
     * 특정 이벤트의 완주자 수 조회
     * 
     * @param eventId 이벤트 ID
     * @return 완주자 수
     */
    @Query("SELECT COUNT(ep) FROM EventParticipant ep WHERE ep.eventId = :eventId AND ep.participationStatus = 'FINISHED'")
    fun countFinishedParticipantsByEventId(@Param("eventId") eventId: Long): Long
    
    /**
     * 특정 이벤트의 카테고리별 참가자 수 조회
     * 
     * @param eventId 이벤트 ID
     * @param category 카테고리
     * @return 카테고리별 참가자 수
     */
    fun countByEventIdAndCategory(eventId: Long, category: String): Long
    
    /**
     * 특정 이벤트의 카테고리별 완주자 수 조회
     * 
     * @param eventId 이벤트 ID
     * @param category 카테고리
     * @return 카테고리별 완주자 수
     */
    @Query("SELECT COUNT(ep) FROM EventParticipant ep WHERE ep.eventId = :eventId AND ep.category = :category AND ep.participationStatus = 'FINISHED'")
    fun countFinishedParticipantsByEventIdAndCategory(@Param("eventId") eventId: Long, @Param("category") category: String): Long
    
    // === 시간 기반 조회 메서드 ===
    
    /**
     * 특정 날짜 이후 등록된 참가자 조회
     * 
     * @param eventId 이벤트 ID
     * @param date 기준 날짜
     * @param pageable 페이징 정보
     * @return 신규 참가자 페이지
     */
    fun findByEventIdAndRegisteredAtAfter(eventId: Long, date: LocalDateTime, pageable: Pageable): Page<EventParticipant>
    
    /**
     * 특정 시간 범위 내에 완주한 참가자 조회
     * 
     * @param eventId 이벤트 ID
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @param pageable 페이징 정보
     * @return 완주자 페이지
     */
    @Query("SELECT ep FROM EventParticipant ep WHERE ep.eventId = :eventId AND ep.finishTime BETWEEN :startTime AND :endTime ORDER BY ep.finishTime ASC")
    fun findByEventIdAndFinishTimeBetween(
        @Param("eventId") eventId: Long,
        @Param("startTime") startTime: LocalDateTime,
        @Param("endTime") endTime: LocalDateTime,
        pageable: Pageable
    ): Page<EventParticipant>
    
    // === 팀별 조회 메서드 ===
    
    /**
     * 특정 이벤트의 팀별 참가자 조회
     * 
     * @param eventId 이벤트 ID
     * @param teamName 팀 이름
     * @param pageable 페이징 정보
     * @return 팀 참가자 페이지
     */
    fun findByEventIdAndTeamName(eventId: Long, teamName: String, pageable: Pageable): Page<EventParticipant>
    
    /**
     * 특정 이벤트의 개인 참가자 조회 (팀명이 없는 참가자)
     * 
     * @param eventId 이벤트 ID
     * @param pageable 페이징 정보
     * @return 개인 참가자 페이지
     */
    @Query("SELECT ep FROM EventParticipant ep WHERE ep.eventId = :eventId AND (ep.teamName IS NULL OR ep.teamName = '')")
    fun findIndividualParticipantsByEventId(@Param("eventId") eventId: Long, pageable: Pageable): Page<EventParticipant>
    
    /**
     * 특정 이벤트의 팀 참가자 조회 (팀명이 있는 참가자)
     * 
     * @param eventId 이벤트 ID
     * @param pageable 페이징 정보
     * @return 팀 참가자 페이지
     */
    @Query("SELECT ep FROM EventParticipant ep WHERE ep.eventId = :eventId AND ep.teamName IS NOT NULL AND ep.teamName != ''")
    fun findTeamParticipantsByEventId(@Param("eventId") eventId: Long, pageable: Pageable): Page<EventParticipant>
    
    // === 검색 메서드 ===
    
    /**
     * 배번으로 참가자 검색 (부분 일치)
     * 
     * @param eventId 이벤트 ID
     * @param bibNumber 배번 (부분 일치)
     * @param pageable 페이징 정보
     * @return 참가자 페이지
     */
    fun findByEventIdAndBibNumberContainingIgnoreCase(eventId: Long, bibNumber: String, pageable: Pageable): Page<EventParticipant>
    
    /**
     * 팀명으로 참가자 검색 (부분 일치)
     * 
     * @param eventId 이벤트 ID
     * @param teamName 팀명 (부분 일치)
     * @param pageable 페이징 정보
     * @return 참가자 페이지
     */
    fun findByEventIdAndTeamNameContainingIgnoreCase(eventId: Long, teamName: String, pageable: Pageable): Page<EventParticipant>
    
    // === 관리자 전용 메서드 ===
    
    /**
     * 특정 이벤트의 문제가 있는 참가자 조회 (DNF, DNS)
     * 
     * @param eventId 이벤트 ID
     * @param pageable 페이징 정보
     * @return 문제가 있는 참가자 페이지
     */
    @Query("SELECT ep FROM EventParticipant ep WHERE ep.eventId = :eventId AND ep.participationStatus IN ('DNF', 'DNS')")
    fun findProblematicParticipantsByEventId(@Param("eventId") eventId: Long, pageable: Pageable): Page<EventParticipant>
    
    /**
     * 특별 필요사항이 있는 참가자 조회
     * 
     * @param eventId 이벤트 ID
     * @param pageable 페이징 정보
     * @return 특별 필요사항이 있는 참가자 페이지
     */
    @Query("SELECT ep FROM EventParticipant ep WHERE ep.eventId = :eventId AND ep.specialRequirements IS NOT NULL AND ep.specialRequirements != ''")
    fun findParticipantsWithSpecialRequirements(@Param("eventId") eventId: Long, pageable: Pageable): Page<EventParticipant>
    
    // === 유틸리티 메서드 ===
    
    /**
     * 참가자 기본 정보 조회 (이름, 배번, 상태)
     * 
     * @param eventId 이벤트 ID
     * @param userId 사용자 ID
     * @return 기본 정보 (배번, 상태, 카테고리)
     */
    @Query("SELECT new map(ep.bibNumber as bibNumber, ep.participationStatus as status, ep.category as category) FROM EventParticipant ep WHERE ep.eventId = :eventId AND ep.userId = :userId")
    fun findBasicInfoByEventIdAndUserId(@Param("eventId") eventId: Long, @Param("userId") userId: Long): Map<String, Any>?
    
    /**
     * 사용자 ID로 참가자 기본 정보 조회 (여러 이벤트)
     * 
     * @param userId 사용자 ID
     * @return 기본 정보 목록 (이벤트별)
     */
    @Query("SELECT new map(ep.eventId as eventId, ep.bibNumber as bibNumber, ep.participationStatus as status, ep.category as category) FROM EventParticipant ep WHERE ep.userId = :userId")
    fun findBasicInfoByUserId(@Param("userId") userId: Long): List<Map<String, Any>>
    
    /**
     * 특정 이벤트에서 사용자가 참가했는지 확인
     * 
     * @param eventId 이벤트 ID
     * @param userId 사용자 ID
     * @return 참가 여부
     */
    fun existsByEventIdAndUserId(eventId: Long, userId: Long): Boolean
    
    /**
     * 특정 이벤트에서 배번이 사용되었는지 확인
     * 
     * @param eventId 이벤트 ID
     * @param bibNumber 배번
     * @return 사용 여부
     */
    fun existsByEventIdAndBibNumber(eventId: Long, bibNumber: String): Boolean
} 
package com.sponovation.runtrack.repository

import com.sponovation.runtrack.domain.Event
import com.sponovation.runtrack.enums.EventStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate

/**
 * 이벤트 데이터 접근을 위한 Repository 인터페이스
 * 
 * 이벤트 관련 CRUD 연산과 커스텀 쿼리를 제공합니다.
 * Spring Data JPA를 사용하여 기본적인 데이터베이스 연산을 자동화하고,
 * 복잡한 비즈니스 로직을 위한 커스텀 쿼리를 추가 정의합니다.
 */
@Repository
interface EventRepository : JpaRepository<Event, Long> {
    
    /**
     * 이벤트 상태별로 이벤트를 조회합니다
     * 
     * @param status 조회할 이벤트 상태
     * @param pageable 페이징 정보
     * @return 상태에 해당하는 이벤트 페이지
     */
    fun findByEventStatusOrderByEventDateDesc(status: EventStatus, pageable: Pageable): Page<Event>
    
    /**
     * 특정 날짜 범위 내의 이벤트를 조회합니다
     * 
     * @param startDate 시작 날짜
     * @param endDate 종료 날짜
     * @param pageable 페이징 정보
     * @return 날짜 범위에 해당하는 이벤트 페이지
     */
    fun findByEventDateBetweenOrderByEventDateAsc(
        startDate: LocalDate, 
        endDate: LocalDate, 
        pageable: Pageable
    ): Page<Event>
    
    /**
     * 이벤트 이름으로 검색합니다 (부분 일치)
     * 
     * @param eventName 검색할 이벤트 이름 (부분 일치)
     * @param pageable 페이징 정보
     * @return 이름이 포함된 이벤트 페이지
     */
    fun findByEventNameContainingIgnoreCaseOrderByEventDateDesc(
        eventName: String, 
        pageable: Pageable
    ): Page<Event>
    
    /**
     * 현재 참가 신청이 가능한 이벤트를 조회합니다
     * 
     * @param currentDate 현재 날짜
     * @param pageable 페이징 정보
     * @return 참가 신청 가능한 이벤트 페이지
     */
    @Query("""
        SELECT e FROM Event e 
        WHERE e.eventStatus = 'SCHEDULED' 
        AND e.registrationStartDate IS NOT NULL 
        AND e.registrationEndDate IS NOT NULL
        AND e.registrationStartDate <= :currentDate 
        AND e.registrationEndDate >= :currentDate
        ORDER BY e.eventDate ASC
    """)
    fun findOpenRegistrationEvents(
        @Param("currentDate") currentDate: LocalDate,
        pageable: Pageable
    ): Page<Event>
    
    /**
     * 예정된 이벤트 중 가장 가까운 N개를 조회합니다
     * 
     * @param currentDate 현재 날짜
     * @param pageable 페이징 정보
     * @return 가장 가까운 예정된 이벤트들
     */
    @Query("""
        SELECT e FROM Event e 
        WHERE e.eventStatus = 'SCHEDULED' 
        AND e.eventDate >= :currentDate
        ORDER BY e.eventDate ASC
    """)
    fun findUpcomingEvents(
        @Param("currentDate") currentDate: LocalDate,
        pageable: Pageable
    ): Page<Event>
    
    /**
     * 특정 월의 이벤트 개수를 조회합니다
     * 
     * @param year 연도
     * @param month 월
     * @return 해당 월의 이벤트 개수
     */
    @Query("""
        SELECT COUNT(e) FROM Event e 
        WHERE YEAR(e.eventDate) = :year 
        AND MONTH(e.eventDate) = :month
    """)
    fun countEventsByMonth(
        @Param("year") year: Int,
        @Param("month") month: Int
    ): Long
    
    /**
     * 상태별 이벤트 개수를 조회합니다
     * 
     * @param status 조회할 이벤트 상태
     * @return 해당 상태의 이벤트 개수
     */
    fun countByEventStatus(status: EventStatus): Long
} 
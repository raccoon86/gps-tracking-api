package com.sponovation.runtrack.repository

import com.sponovation.runtrack.domain.EventDetail
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

/**
 * 코스 데이터 접근을 위한 Repository 인터페이스
 * 
 * 코스 관련 CRUD 연산과 커스텀 쿼리를 제공합니다.
 * Spring Data JPA를 사용하여 기본적인 데이터베이스 연산을 자동화하고,
 * 코스 관련 비즈니스 로직을 위한 커스텀 쿼리를 추가 정의합니다.
 */
@Repository
interface CourseRepository : JpaRepository<EventDetail, Long> {
    /**
     * 특정 이벤트에 속한 모든 코스를 조회합니다 (리스트)
     * 
     * @param eventId 조회할 이벤트 ID
     * @return 이벤트에 속한 코스 리스트
     */
    fun findByEventIdOrderByDistanceAsc(eventId: Long): List<EventDetail>
}
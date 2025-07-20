package com.sponovation.runtrack.repository

import com.sponovation.runtrack.domain.EventDetail
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * 이벤트 상세 정보 Repository
 */
@Repository
interface EventDetailRepository : JpaRepository<EventDetail, Long> {
    /**
     * 이벤트 ID로 이벤트 상세 목록 조회 (거리순 정렬)
     */
    fun findByEventIdOrderByDistanceAsc(eventId: Long): List<EventDetail>
} 
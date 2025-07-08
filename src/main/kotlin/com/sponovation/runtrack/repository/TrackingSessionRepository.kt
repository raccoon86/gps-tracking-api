package com.sponovation.runtrack.repository

import com.sponovation.runtrack.domain.TrackingSession
import com.sponovation.runtrack.domain.TrackingStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface TrackingSessionRepository : JpaRepository<TrackingSession, Long> {

    fun findByUserId(userId: String): List<TrackingSession>

    fun findByUserIdAndStatus(userId: String, status: TrackingStatus): List<TrackingSession>

    @Query(
        """
        SELECT t FROM TrackingSession t 
        WHERE t.userId = :userId 
        AND t.status IN ('STARTED', 'PAUSED')
    """
    )
    fun findActiveSessionsByUserId(userId: String): List<TrackingSession>

    fun findByUserIdOrderByStartTimeDesc(userId: String): List<TrackingSession>
} 

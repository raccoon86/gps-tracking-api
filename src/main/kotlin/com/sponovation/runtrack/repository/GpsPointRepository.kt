package com.sponovation.runtrack.repository

import com.sponovation.runtrack.domain.GpsPoint
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface GpsPointRepository : JpaRepository<GpsPoint, Long> {

    fun findByTrackingSessionId(sessionId: Long): List<GpsPoint>

    fun findByTrackingSessionIdOrderByTimestampAsc(sessionId: Long): List<GpsPoint>

    @Query(
        """
        SELECT g FROM GpsPoint g 
        WHERE g.trackingSession.id = :sessionId 
        AND g.timestamp BETWEEN :startTime AND :endTime
        ORDER BY g.timestamp ASC
    """
    )
    fun findBySessionIdAndTimeBetween(
        @Param("sessionId") sessionId: Long,
        @Param("startTime") startTime: LocalDateTime,
        @Param("endTime") endTime: LocalDateTime
    ): List<GpsPoint>

    @Query(
        """
        SELECT g FROM GpsPoint g 
        WHERE g.trackingSession.id = :sessionId 
        ORDER BY g.timestamp DESC 
        LIMIT 1
    """
    )
    fun findLatestBySessionId(@Param("sessionId") sessionId: Long): GpsPoint?
} 

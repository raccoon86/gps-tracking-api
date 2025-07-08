package com.sponovation.runtrack.repository

import com.sponovation.runtrack.domain.GpxRoute
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface GpxRouteRepository : JpaRepository<GpxRoute, Long> {

    fun findByNameContainingIgnoreCase(name: String): List<GpxRoute>

    @Query(
        """
        SELECT g FROM GpxRoute g
        ORDER BY g.createdAt DESC
    """
    )
    fun findAllOrderByCreatedAtDesc(): List<GpxRoute>
}

interface GpxWaypointRepository : JpaRepository<com.sponovation.runtrack.domain.GpxWaypoint, Long> {

    fun findByGpxRouteIdOrderBySequenceAsc(gpxRouteId: Long): List<com.sponovation.runtrack.domain.GpxWaypoint>
}

interface CheckpointRepository : JpaRepository<com.sponovation.runtrack.domain.Checkpoint, Long> {

    fun findByGpxRouteIdOrderBySequenceAsc(gpxRouteId: Long): List<com.sponovation.runtrack.domain.Checkpoint>
}

interface CheckpointReachRepository : JpaRepository<com.sponovation.runtrack.domain.CheckpointReach, Long> {

    fun findByTrackingSessionId(trackingSessionId: Long): List<com.sponovation.runtrack.domain.CheckpointReach>

    fun findByTrackingSessionIdAndCheckpointId(trackingSessionId: Long, checkpointId: Long): com.sponovation.runtrack.domain.CheckpointReach?
} 

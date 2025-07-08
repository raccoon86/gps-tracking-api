package com.sponovation.runtrack.dto

import com.sponovation.runtrack.domain.TrackingStatus
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

data class StartTrackingRequest(
    @field:NotNull
    val userId: String,

    val gpxRouteId: Long? = null
)

data class TrackingSessionResponse(
    val id: Long,
    val userId: String,
    val status: TrackingStatus,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime? = null,
    val totalDistance: Double,
    val totalDuration: Long,
    val averageSpeed: Double,
    val maxSpeed: Double,
    val totalElevationGain: Double,
    val gpxRoute: GpxRouteInfo? = null
)

data class GpxRouteInfo(
    val id: Long,
    val name: String,
    val description: String,
    val totalDistance: Double,
    val totalElevationGain: Double,
    val totalElevationLoss: Double
)

data class TrackingStatistics(
    val totalDistance: Double,
    val totalDuration: Long,
    val averageSpeed: Double,
    val maxSpeed: Double,
    val totalElevationGain: Double,
    val checkpointsReached: Int,
    val totalCheckpoints: Int,
    val routeCompletion: Double // percentage
) 

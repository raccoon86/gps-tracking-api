package com.sponovation.runtrack.dto

import com.sponovation.runtrack.enums.TrackingStatus
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

data class StartTrackingRequest(
    @field:NotNull
    val userId: String,

    // val gpxRouteId: Long? = null - GpxRoute 엔티티 삭제로 사용 안함
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
    // val gpxRoute: GpxRouteInfo? = null - GpxRoute 엔티티 삭제로 사용 안함
)

// GpxRoute 엔티티 삭제로 비활성화
// data class GpxRouteInfo(
//     val id: Long,
//     val name: String,
//     val description: String,
//     val totalDistance: Double,
//     val totalElevationGain: Double,
//     val totalElevationLoss: Double
// )

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

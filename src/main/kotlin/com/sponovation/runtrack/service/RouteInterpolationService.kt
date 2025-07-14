package com.sponovation.runtrack.service

import com.sponovation.runtrack.util.GeoUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import kotlin.math.*
import com.fasterxml.jackson.annotation.JsonInclude

@Service
class RouteInterpolationService {

    private val logger = LoggerFactory.getLogger(RouteInterpolationService::class.java)

    companion object {
        private const val INTERPOLATION_DISTANCE_METERS = 100.0
    }

    /**
     * GPX 웨이포인트를 100미터 간격으로 보간합니다.
     */
    fun interpolateRoute(waypoints: List<ParsedGpxWaypoint>): List<InterpolatedPoint> {
        // 입력값 유효성 검증
        require(waypoints.isNotEmpty()) { "입력 웨이포인트가 비어있습니다." }
        require(waypoints.size >= 2) { "보간을 위해서는 최소 2개 이상의 웨이포인트가 필요합니다." }
        waypoints.forEachIndexed { idx, pt ->
            require(pt.latitude in -90.0..90.0) { "[$idx] latitude 범위 오류: ${pt.latitude}" }
            require(pt.longitude in -180.0..180.0) { "[$idx] longitude 범위 오류: ${pt.longitude}" }
            require(!pt.elevation.isNaN()) { "[$idx] elevation 값이 NaN입니다." }
        }

        if (waypoints.size < 2) {
            logger.warn("보간할 웨이포인트가 부족합니다: ${waypoints.size}개")
            return waypoints.map { 
                InterpolatedPoint(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    elevation = it.elevation,
                    distanceFromStart = 0.0
                )
            }
        }

        val interpolatedPoints = mutableListOf<InterpolatedPoint>()
        var totalDistance = 0.0

        // 첫 번째 포인트 추가
        interpolatedPoints.add(
            InterpolatedPoint(
                latitude = waypoints[0].latitude,
                longitude = waypoints[0].longitude,
                elevation = waypoints[0].elevation,
                distanceFromStart = 0.0
            )
        )

        for (i in 1 until waypoints.size) {
            val startPoint = waypoints[i - 1]
            val endPoint = waypoints[i]
            
            val segmentDistance = GeoUtils.calculateDistance(
                startPoint.latitude, startPoint.longitude,
                endPoint.latitude, endPoint.longitude
            )
            
            // 세그먼트가 100m보다 긴 경우 보간 포인트 생성
            if (segmentDistance > INTERPOLATION_DISTANCE_METERS) {
                val numInterpolations = (segmentDistance / INTERPOLATION_DISTANCE_METERS).toInt()
                
                for (j in 1..numInterpolations) {
                    val ratio = (j * INTERPOLATION_DISTANCE_METERS) / segmentDistance
                    val interpolatedLat = startPoint.latitude + (endPoint.latitude - startPoint.latitude) * ratio
                    val interpolatedLng = startPoint.longitude + (endPoint.longitude - startPoint.longitude) * ratio
                    val interpolatedElevation = startPoint.elevation + (endPoint.elevation - startPoint.elevation) * ratio
                    
                    totalDistance += INTERPOLATION_DISTANCE_METERS
                    
                    interpolatedPoints.add(
                        InterpolatedPoint(
                            latitude = interpolatedLat,
                            longitude = interpolatedLng,
                            elevation = interpolatedElevation,
                            distanceFromStart = totalDistance
                        )
                    )
                }
                
                // 남은 거리 추가
                val remainingDistance = segmentDistance - (numInterpolations * INTERPOLATION_DISTANCE_METERS)
                totalDistance += remainingDistance
            } else {
                totalDistance += segmentDistance
            }
            
            // 원본 끝점 추가
            interpolatedPoints.add(
                InterpolatedPoint(
                    latitude = endPoint.latitude,
                    longitude = endPoint.longitude,
                    elevation = endPoint.elevation,
                    distanceFromStart = totalDistance
                )
            )
        }

        logger.info("경로 보간 완료: ${interpolatedPoints.size}개 포인트 (총 거리: ${totalDistance}m)")
        return interpolatedPoints
    }

    /**
     * 경로의 총 거리를 계산합니다.
     */
    fun calculateTotalDistance(waypoints: List<ParsedGpxWaypoint>): Double {
        if (waypoints.size < 2) return 0.0

        var totalDistance = 0.0
        for (i in 1 until waypoints.size) {
            val prevPoint = waypoints[i - 1]
            val currentPoint = waypoints[i]
            totalDistance += GeoUtils.calculateDistance(
                prevPoint.latitude, prevPoint.longitude,
                currentPoint.latitude, currentPoint.longitude
            )
        }
        return totalDistance
    }
}

/**
 * 보간된 포인트 데이터 클래스
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class InterpolatedPoint(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double,
    val distanceFromStart: Double
) 
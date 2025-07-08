package com.sponovation.runtrack.util

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import kotlin.math.abs

/**
 * GeoUtils의 보간 포인트 생성 기능 테스트
 */
class GeoUtilsInterpolationTest {

    @Test
    fun `두 점 사이의 거리가 100미터 이하일 때 보간 포인트가 생성되지 않아야 함`() {
        // Given: 서울 시청 근처의 두 점 (약 50미터 거리)
        val lat1 = 37.5663
        val lon1 = 126.9779
        val lat2 = 37.5668
        val lon2 = 126.9784
        val intervalMeters = 100.0

        // When: 보간 포인트 생성
        val interpolatedPoints = GeoUtils.generateInterpolatedPoints(
            lat1, lon1, 100.0,
            lat2, lon2, 120.0,
            intervalMeters
        )

        // Then: 보간 포인트가 생성되지 않아야 함
        assertTrue(interpolatedPoints.isEmpty())
    }

    @Test
    fun `두 점 사이의 거리가 200미터일 때 1개의 보간 포인트가 생성되어야 함`() {
        // Given: 약 200미터 거리의 두 점
        val lat1 = 37.5663
        val lon1 = 126.9779
        val lat2 = 37.5681  // 약 200미터 북쪽
        val lon2 = 126.9779
        val intervalMeters = 100.0

        // When: 보간 포인트 생성
        val interpolatedPoints = GeoUtils.generateInterpolatedPoints(
            lat1, lon1, 100.0,
            lat2, lon2, 120.0,
            intervalMeters
        )

        // Then: 1개의 보간 포인트가 생성되어야 함
        assertEquals(1, interpolatedPoints.size)

        val (interpLat, interpLon, interpElev) = interpolatedPoints[0]
        
        // 보간 포인트가 중점 근처에 있어야 함
        val expectedMidLat = (lat1 + lat2) / 2
        val expectedMidLon = (lon1 + lon2) / 2
        
        assertTrue(abs(interpLat - expectedMidLat) < 0.0001)
        assertTrue(abs(interpLon - expectedMidLon) < 0.0001)
        
        // 고도도 보간되어야 함
        assertEquals(110.0, interpElev!!, 0.1)
    }

    @Test
    fun `두 점 사이의 거리가 350미터일 때 3개의 보간 포인트가 생성되어야 함`() {
        // Given: 약 350미터 거리의 두 점
        val lat1 = 37.5663
        val lon1 = 126.9779
        val lat2 = 37.5695  // 약 350미터 북쪽
        val lon2 = 126.9779
        val intervalMeters = 100.0

        // When: 보간 포인트 생성
        val interpolatedPoints = GeoUtils.generateInterpolatedPoints(
            lat1, lon1, 50.0,
            lat2, lon2, 200.0,
            intervalMeters
        )

        // Then: 3개의 보간 포인트가 생성되어야 함 (100m, 200m, 300m 지점)
        assertEquals(3, interpolatedPoints.size)

        // 각 보간 포인트의 거리를 검증
        for (i in interpolatedPoints.indices) {
            val (interpLat, interpLon, interpElev) = interpolatedPoints[i]
            
            // 시작점으로부터의 거리 확인
            val distanceFromStart = GeoUtils.calculateDistance(lat1, lon1, interpLat, interpLon)
            val expectedDistance = (i + 1) * intervalMeters
            
            // 허용 오차 10미터 이내
            assertTrue(abs(distanceFromStart - expectedDistance) < 10.0,
                "보간 포인트 ${i + 1}의 거리가 예상과 다름: 실제=${String.format("%.1f", distanceFromStart)}m, 예상=${expectedDistance}m")
        }
    }

    @Test
    fun `고도 정보가 없는 경우에도 보간 포인트가 생성되어야 함`() {
        // Given: 고도 정보가 없는 두 점
        val lat1 = 37.5663
        val lon1 = 126.9779
        val lat2 = 37.5681
        val lon2 = 126.9779
        val intervalMeters = 100.0

        // When: 보간 포인트 생성 (고도 null)
        val interpolatedPoints = GeoUtils.generateInterpolatedPoints(
            lat1, lon1, null,
            lat2, lon2, null,
            intervalMeters
        )

        // Then: 보간 포인트가 생성되고 고도는 null이어야 함
        assertEquals(1, interpolatedPoints.size)
        assertNull(interpolatedPoints[0].third)
    }

    @Test
    fun `한 점만 고도 정보가 있는 경우 해당 고도가 사용되어야 함`() {
        // Given: 한 점만 고도 정보가 있는 경우
        val lat1 = 37.5663
        val lon1 = 126.9779
        val lat2 = 37.5681
        val lon2 = 126.9779
        val intervalMeters = 100.0

        // When: 보간 포인트 생성 (첫 번째 점만 고도 있음)
        val interpolatedPoints = GeoUtils.generateInterpolatedPoints(
            lat1, lon1, 150.0,
            lat2, lon2, null,
            intervalMeters
        )

        // Then: 보간 포인트의 고도는 첫 번째 점의 고도를 사용해야 함
        assertEquals(1, interpolatedPoints.size)
        assertEquals(150.0, interpolatedPoints[0].third)
    }
} 
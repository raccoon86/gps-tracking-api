package com.sponovation.runtrack.util

import kotlin.math.*

/**
 * GPS 좌표 및 지리적 계산을 위한 유틸리티 클래스
 * 
 * 이 클래스는 두 지점 간의 거리, 방위각, 속도 등을 계산하는 기능을 제공합니다.
 * 모든 계산은 WGS84 좌표계를 기준으로 하며, 지구를 완전한 구로 가정합니다.
 */
object GeoUtils {

    /** 지구 반지름 (킬로미터 단위) - WGS84 기준 평균 반지름 */
    private const val EARTH_RADIUS_KM = 6371.0
    
    /** 지구 반지름 (미터 단위) - 대부분의 계산에서 사용 */
    private const val EARTH_RADIUS_M = EARTH_RADIUS_KM * 1000

    /**
     * 두 GPS 좌표 간의 직선 거리를 계산합니다 (Haversine formula 사용)
     * 
     * Haversine 공식은 구면 삼각법을 사용하여 지구 표면상의 두 점 사이의 
     * 최단 거리(great circle distance)를 계산합니다. 지구를 완전한 구로 
     * 가정하므로 약간의 오차가 있을 수 있습니다 (일반적으로 0.5% 이내).
     * 
     * @param lat1 첫 번째 지점의 위도 (도 단위, -90 ~ 90)
     * @param lon1 첫 번째 지점의 경도 (도 단위, -180 ~ 180)
     * @param lat2 두 번째 지점의 위도 (도 단위, -90 ~ 90)
     * @param lon2 두 번째 지점의 경도 (도 단위, -180 ~ 180)
     * @return 두 지점 간의 거리 (미터 단위)
     * 
     * 사용 예시:
     * val distance = calculateDistance(37.5665, 126.9780, 35.1595, 129.0756) // 서울-부산 거리
     */
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        // 위도와 경도의 차이를 라디안으로 변환
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        // Haversine 공식의 핵심 계산
        // a = sin²(Δφ/2) + cos φ1 ⋅ cos φ2 ⋅ sin²(Δλ/2)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)

        // 중심각 계산: c = 2 ⋅ atan2(√a, √(1−a))
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        // 거리 = 지구 반지름 × 중심각
        return EARTH_RADIUS_M * c
    }

    /**
     * 두 GPS 좌표 간의 방위각(bearing)을 계산합니다
     * 
     * 방위각은 첫 번째 지점에서 두 번째 지점을 향하는 방향을 나타내며,
     * 북쪽을 0도로 하여 시계방향으로 증가합니다.
     * 
     * @param lat1 시작 지점의 위도 (도 단위, -90 ~ 90)
     * @param lon1 시작 지점의 경도 (도 단위, -180 ~ 180)
     * @param lat2 목표 지점의 위도 (도 단위, -90 ~ 90) 
     * @param lon2 목표 지점의 경도 (도 단위, -180 ~ 180)
     * @return 방위각 (도 단위, 0 ~ 360)
     *         - 0도: 북쪽, 90도: 동쪽, 180도: 남쪽, 270도: 서쪽
     * 
     * 사용 예시:
     * val bearing = calculateBearing(37.5665, 126.9780, 35.1595, 129.0756) // 서울에서 부산 방향
     */
    fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        // 경도 차이를 라디안으로 변환
        val dLon = Math.toRadians(lon2 - lon1)
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)

        // 방위각 계산을 위한 좌표 변환
        // y = sin(Δlong) × cos(lat2)
        val y = sin(dLon) * cos(lat2Rad)
        // x = cos(lat1) × sin(lat2) − sin(lat1) × cos(lat2) × cos(Δlong)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLon)

        // atan2를 사용하여 방위각 계산 후 도 단위로 변환
        val bearing = atan2(y, x)
        // 결과를 0-360도 범위로 정규화
        return (Math.toDegrees(bearing) + 360) % 360
    }

    /**
     * 한 점에서 선분까지의 최단 거리를 계산합니다
     * 
     * 이 함수는 점에서 선분에 수직으로 내린 발까지의 거리를 계산합니다.
     * 수직선의 발이 선분 밖에 있는 경우, 선분의 끝점까지의 거리를 반환합니다.
     * 경로 이탈 감지나 네비게이션 시스템에서 유용합니다.
     * 
     * @param pointLat 대상 점의 위도 (도 단위)
     * @param pointLon 대상 점의 경도 (도 단위)
     * @param line1Lat 선분 시작점의 위도 (도 단위)
     * @param line1Lon 선분 시작점의 경도 (도 단위)
     * @param line2Lat 선분 끝점의 위도 (도 단위)
     * @param line2Lon 선분 끝점의 경도 (도 단위)
     * @return 점에서 선분까지의 최단 거리 (미터 단위)
     * 
     * 계산 방법:
     * 1. 점에서 선분에 수직선을 내림
     * 2. 수직선의 발이 선분 내부에 있으면 그 거리를 반환
     * 3. 수직선의 발이 선분 밖에 있으면 가장 가까운 끝점까지의 거리를 반환
     */
    fun distanceToLineSegment(
        pointLat: Double,
        pointLon: Double,
        line1Lat: Double,
        line1Lon: Double,
        line2Lat: Double,
        line2Lon: Double
    ): Double {
        // 점과 선분 시작점 사이의 벡터
        val A = pointLat - line1Lat
        val B = pointLon - line1Lon
        
        // 선분을 나타내는 벡터
        val C = line2Lat - line1Lat
        val D = line2Lon - line1Lon

        // 벡터의 내적 계산
        val dot = A * C + B * D
        // 선분 벡터의 길이의 제곱
        val lenSq = C * C + D * D

        // 선분의 길이가 0인 경우 (두 점이 같은 경우)
        if (lenSq == 0.0) {
            return calculateDistance(pointLat, pointLon, line1Lat, line1Lon)
        }

        // 선분 위의 가장 가까운 점을 찾기 위한 매개변수
        val param = dot / lenSq

        // 가장 가까운 점의 좌표 계산
        val closestLat: Double
        val closestLon: Double

        when {
            // 수직선의 발이 선분 시작점 이전에 있는 경우
            param < 0 -> {
                closestLat = line1Lat
                closestLon = line1Lon
            }
            // 수직선의 발이 선분 끝점 이후에 있는 경우
            param > 1 -> {
                closestLat = line2Lat
                closestLon = line2Lon
            }
            // 수직선의 발이 선분 내부에 있는 경우
            else -> {
                closestLat = line1Lat + param * C
                closestLon = line1Lon + param * D
            }
        }

        // 점과 가장 가까운 지점 사이의 거리 반환
        return calculateDistance(pointLat, pointLon, closestLat, closestLon)
    }

    /**
     * GPS 좌표가 지정된 원형 영역 내에 있는지 확인합니다
     * 
     * 이 함수는 지오펜싱(geofencing) 기능 구현에 유용합니다.
     * 특정 위치를 중심으로 한 원형 구역 내에 사용자가 있는지 판단할 때 사용합니다.
     * 
     * @param pointLat 확인할 점의 위도 (도 단위)
     * @param pointLon 확인할 점의 경도 (도 단위)
     * @param centerLat 원형 영역 중심점의 위도 (도 단위)
     * @param centerLon 원형 영역 중심점의 경도 (도 단위)
     * @param radiusMeters 원형 영역의 반지름 (미터 단위)
     * @return 점이 원형 영역 내에 있으면 true, 없으면 false
     * 
     * 사용 예시:
     * val isNearby = isWithinRadius(37.5665, 126.9780, 37.5663, 126.9779, 100.0) // 100m 반경 내 확인
     */
    fun isWithinRadius(
        pointLat: Double,
        pointLon: Double,
        centerLat: Double,
        centerLon: Double,
        radiusMeters: Double
    ): Boolean {
        // 두 점 사이의 실제 거리 계산
        val distance = calculateDistance(pointLat, pointLon, centerLat, centerLon)
        // 계산된 거리가 반지름 이하인지 확인
        return distance <= radiusMeters
    }

    /**
     * 두 GPS 좌표와 시간 정보를 이용하여 이동 속도를 계산합니다
     * 
     * 실제 GPS 추적에서 순간 속도를 계산할 때 사용합니다.
     * 연속된 두 GPS 포인트 사이의 평균 속도를 구합니다.
     * 
     * @param lat1 첫 번째 위치의 위도 (도 단위)
     * @param lon1 첫 번째 위치의 경도 (도 단위)
     * @param time1Millis 첫 번째 위치의 시간 (밀리초 단위 Unix timestamp)
     * @param lat2 두 번째 위치의 위도 (도 단위)
     * @param lon2 두 번째 위치의 경도 (도 단위)  
     * @param time2Millis 두 번째 위치의 시간 (밀리초 단위 Unix timestamp)
     * @return 평균 이동 속도 (m/s 단위)
     *         - 시간 차이가 0 이하인 경우 0.0 반환
     *         - km/h로 변환하려면 결과에 3.6을 곱하세요
     * 
     * 주의사항:
     * - GPS 정확도에 따라 결과가 부정확할 수 있습니다
     * - 매우 짧은 시간 간격에서는 노이즈의 영향을 받을 수 있습니다
     * - 실제 경로와 직선 거리의 차이로 인해 실제 속도보다 낮게 계산될 수 있습니다
     * 
     * 사용 예시:
     * val speedMs = calculateSpeed(37.5665, 126.9780, 1000000, 37.5663, 126.9779, 1010000)
     * val speedKmh = speedMs * 3.6 // km/h로 변환
     */
    fun calculateSpeed(
        lat1: Double,
        lon1: Double,
        time1Millis: Long,
        lat2: Double,
        lon2: Double,
        time2Millis: Long
    ): Double {
        // 두 점 사이의 직선 거리 계산 (미터 단위)
        val distance = calculateDistance(lat1, lon1, lat2, lon2)
        // 시간 차이를 초 단위로 변환
        val timeDiff = (time2Millis - time1Millis) / 1000.0

        // 시간 차이가 양수인 경우에만 속도 계산, 그렇지 않으면 0 반환
        return if (timeDiff > 0) distance / timeDiff else 0.0
    }

    /**
     * 두 GPS 좌표 사이에 지정된 간격으로 보간 포인트를 생성합니다
     * 
     * 두 포인트 사이의 거리가 지정된 간격보다 클 경우, 
     * 선형 보간을 사용하여 중간에 포인트들을 생성합니다.
     * GPS 트랙 데이터에서 포인트 밀도를 균일하게 만들 때 유용합니다.
     * 
     * @param lat1 시작 지점의 위도 (도 단위)
     * @param lon1 시작 지점의 경도 (도 단위)
     * @param elev1 시작 지점의 고도 (미터, null 허용)
     * @param lat2 끝 지점의 위도 (도 단위)
     * @param lon2 끝 지점의 경도 (도 단위)
     * @param elev2 끝 지점의 고도 (미터, null 허용)
     * @param intervalMeters 포인트 간 간격 (미터)
     * @return 보간된 포인트들의 리스트 (시작점과 끝점 제외)
     *         각 포인트는 [위도, 경도, 고도] 형태의 Triple
     * 
     * 사용 예시:
     * val interpolatedPoints = generateInterpolatedPoints(
     *     37.5665, 126.9780, 100.0,
     *     37.5675, 126.9790, 120.0,
     *     100.0 // 100미터 간격
     * )
     */
    fun generateInterpolatedPoints(
        lat1: Double,
        lon1: Double,
        elev1: Double?,
        lat2: Double,
        lon2: Double,
        elev2: Double?,
        intervalMeters: Double
    ): List<Triple<Double, Double, Double?>> {
        // 두 점 사이의 총 거리 계산
        val totalDistance = calculateDistance(lat1, lon1, lat2, lon2)
        
        // 간격보다 거리가 작으면 보간 포인트 생성하지 않음
        if (totalDistance <= intervalMeters) {
            return emptyList()
        }
        
        val interpolatedPoints = mutableListOf<Triple<Double, Double, Double?>>()
        var currentDistance = intervalMeters
        
        // 지정된 간격마다 보간 포인트 생성 (끝점 근처는 제외)
        while (currentDistance < totalDistance - (intervalMeters * 0.1)) { // 끝점으로부터 간격의 10% 이내는 제외
            // 보간 비율 계산 (0.0 ~ 1.0)
            val ratio = currentDistance / totalDistance
            
            // 위도와 경도 선형 보간
            val interpLat = lat1 + (lat2 - lat1) * ratio
            val interpLon = lon1 + (lon2 - lon1) * ratio
            
            // 고도 선형 보간 (둘 다 유효한 경우만)
            val interpElev = if (elev1 != null && elev2 != null) {
                elev1 + (elev2 - elev1) * ratio
            } else {
                elev1 ?: elev2 // 하나만 유효한 경우 그 값 사용
            }
            
            interpolatedPoints.add(Triple(interpLat, interpLon, interpElev))
            currentDistance += intervalMeters
        }
        
        return interpolatedPoints
    }
} 

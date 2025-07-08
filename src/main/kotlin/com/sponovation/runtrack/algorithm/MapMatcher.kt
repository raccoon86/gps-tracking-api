package com.sponovation.runtrack.algorithm

import com.sponovation.runtrack.domain.GpxWaypoint
import com.sponovation.runtrack.service.InterpolatedPoint
import com.sponovation.runtrack.util.GeoUtils

/**
 * GPS 좌표를 GPX 경로에 매칭하는 맵 매처
 * 
 * 이 클래스는 실제 GPS 위치를 미리 정의된 경로(GPX 파일)의 가장 적절한 지점으로 매칭하는 기능을 제공합니다.
 * GPS 신호의 부정확성으로 인해 발생하는 위치 오차를 보정하여, 사용자가 실제로 경로상의 어느 지점에 있는지 
 * 정확하게 추정할 수 있도록 합니다.
 * 
 * 주요 기능:
 * - GPS 좌표를 경로의 가장 가까운 세그먼트에 투영
 * - 거리와 방향을 고려한 종합적인 매칭 점수 계산
 * - 경로상의 진행률 계산
 * 
 * 매칭 알고리즘:
 * 1. 각 경로 세그먼트에 대해 GPS 좌표를 투영
 * 2. 거리 기반 점수와 방향 기반 점수를 가중평균으로 계산
 * 3. 가장 낮은 점수(최적 매칭)를 가진 세그먼트 선택
 * 4. 임계값 내에 있을 경우 매칭 성공으로 판정
 */
class MapMatcher {

    companion object {
        /**
         * 매칭 성공으로 판단하는 최대 거리 임계값 (미터)
         * 이 거리보다 멀리 떨어진 경우 매칭 실패로 간주합니다.
         * 100미터는 일반적인 GPS 오차 범위와 도로폭을 고려한 적절한 값입니다.
         */
        private const val MAX_DISTANCE_THRESHOLD = 100.0 // meters
        
        /**
         * 매칭 점수 계산 시 거리의 가중치
         * 전체 점수에서 거리가 차지하는 비중을 나타냅니다.
         * 거리가 방향보다 더 중요한 요소로 간주되어 0.6(60%)의 가중치를 가집니다.
         */
        private const val WEIGHT_DISTANCE = 0.6
        
        /**
         * 매칭 점수 계산 시 방향(베어링)의 가중치
         * 전체 점수에서 방향이 차지하는 비중을 나타냅니다.
         * 거리보다는 덜 중요하지만 여전히 고려해야 할 요소로 0.4(40%)의 가중치를 가집니다.
         */
        private const val WEIGHT_BEARING = 0.4
    }

    /**
     * GPS 좌표를 GPX 경로의 가장 가까운 세그먼트에 매칭하는 주요 함수
     * 
     * 이 함수는 현재 GPS 위치와 이동 방향을 분석하여, 미리 정의된 경로상의 
     * 가장 적절한 지점을 찾아 매칭 결과를 반환합니다.
     * 
     * 처리 과정:
     * 1. 입력 검증: 최소 2개 이상의 웨이포인트가 필요
     * 2. 각 경로 세그먼트에 대해 매칭 후보 생성
     * 3. 거리와 방향을 고려한 종합 점수 계산
     * 4. 최적 매칭 선택 및 결과 반환
     * 
     * @param gpsLat GPS에서 제공하는 현재 위도 (WGS84 좌표계)
     * @param gpsLon GPS에서 제공하는 현재 경도 (WGS84 좌표계)
     * @param bearing 현재 이동 방향 (북쪽 기준 시계방향 각도, 0-360도)
     * @param waypoints 경로를 구성하는 웨이포인트 리스트 (순서대로 정렬되어야 함)
     * @return 매칭 결과를 포함하는 MatchResult 객체
     */
    fun matchToRoute(
        gpsLat: Double,
        gpsLon: Double,
        bearing: Double,
        waypoints: List<GpxWaypoint>
    ): MatchResult {
        // 경로 세그먼트를 구성하기 위해서는 최소 2개의 점이 필요
        // 점이 하나뿐이면 선분을 만들 수 없어 매칭 불가능
        if (waypoints.size < 2) {
            return MatchResult(
                matched = false, // 매칭 실패
                distanceToRoute = Double.MAX_VALUE, // 거리 측정 불가능
                matchedLatitude = gpsLat, // 원본 GPS 좌표 유지
                matchedLongitude = gpsLon,
                routeProgress = 0.0 // 진행률 0%
            )
        }

        // 최적 매칭을 찾기 위한 변수들 초기화
        var bestMatch: RouteMatch? = null // 현재까지 발견된 최적 매칭
        var minScore = Double.MAX_VALUE // 최소 점수 (점수가 낮을수록 더 좋은 매칭)

        // 모든 경로 세그먼트에 대해 매칭 후보를 생성하고 평가
        // 세그먼트는 연속된 두 웨이포인트 사이의 직선 구간을 의미
        for (segmentIndex in 0 until waypoints.size - 1) {
            val startPoint = waypoints[segmentIndex] // 현재 세그먼트의 시작점
            val endPoint = waypoints[segmentIndex + 1] // 현재 세그먼트의 끝점

            // 현재 세그먼트에서 GPS 좌표에 대한 최적 매칭점 탐색
            val matchCandidate = findBestMatchOnSegment(
                gpsLat,
                gpsLon,
                bearing,
                startPoint.latitude,
                startPoint.longitude,
                endPoint.latitude,
                endPoint.longitude
            )

            // 매칭 후보의 품질을 나타내는 종합 점수 계산
            // 점수가 낮을수록 더 좋은 매칭을 의미
            val matchScore = calculateMatchScore(matchCandidate, bearing)

            // 현재까지의 최적 매칭보다 더 좋은 점수인 경우 업데이트
            if (matchScore < minScore) {
                minScore = matchScore
                bestMatch = RouteMatch(
                    matchCandidate,
                    segmentIndex, // 매칭된 세그먼트의 인덱스
                    startPoint.distanceFromStart + matchCandidate.distanceAlongSegment // 전체 경로에서의 누적 거리
                )
            }
        }

        // 최적 매칭이 발견된 경우 최종 결과 생성
        bestMatch?.let { match ->
            // 거리 임계값을 기준으로 매칭 성공 여부 판정
            val isMatched = match.candidate.distanceToSegment <= MAX_DISTANCE_THRESHOLD
            
            // 전체 경로 길이 계산 (마지막 웨이포인트의 누적 거리)
            val totalDistance = waypoints.lastOrNull()?.distanceFromStart ?: 0.0
            
            // 전체 경로에서의 진행률 계산 (0.0 ~ 1.0 범위)
            val routeProgress = if (totalDistance > 0) match.progressDistance / totalDistance else 0.0

            return MatchResult(
                matched = isMatched,
                distanceToRoute = match.candidate.distanceToSegment,
                // 매칭 성공 시 보정된 좌표, 실패 시 원본 GPS 좌표 사용
                matchedLatitude = if (isMatched) match.candidate.projectedLat else gpsLat,
                matchedLongitude = if (isMatched) match.candidate.projectedLon else gpsLon,
                routeProgress = routeProgress
            )
        }

        // 매칭할 수 있는 세그먼트가 없는 경우 기본 결과 반환
        return MatchResult(
            matched = false,
            distanceToRoute = Double.MAX_VALUE,
            matchedLatitude = gpsLat,
            matchedLongitude = gpsLon,
            routeProgress = 0.0
        )
    }

    /**
     * GPS 좌표를 보간된 경로 포인트에 매칭하는 함수
     * 
     * 이 함수는 CourseDataService에서 100미터 간격으로 보간된 경로 포인트들을 사용하여
     * 더 정밀한 매칭을 수행합니다. 기존 waypoint 기반 매칭보다 더 세밀한 보정이 가능합니다.
     * 
     * @param gpsLat GPS에서 제공하는 현재 위도
     * @param gpsLon GPS에서 제공하는 현재 경도
     * @param bearing 현재 이동 방향 (북쪽 기준 시계방향 각도, 0-360도)
     * @param interpolatedPoints 100미터 간격으로 보간된 경로 포인트 리스트
     * @return 매칭 결과를 포함하는 MatchResult 객체
     */
    fun matchToInterpolatedRoute(
        gpsLat: Double,
        gpsLon: Double,
        bearing: Double,
        interpolatedPoints: List<InterpolatedPoint>
    ): MatchResult {
        // 경로 세그먼트를 구성하기 위해서는 최소 2개의 점이 필요
        if (interpolatedPoints.size < 2) {
            return MatchResult(
                matched = false,
                distanceToRoute = Double.MAX_VALUE,
                matchedLatitude = gpsLat,
                matchedLongitude = gpsLon,
                routeProgress = 0.0
            )
        }

        // 최적 매칭을 찾기 위한 변수들 초기화
        var bestMatch: InterpolatedRouteMatch? = null
        var minScore = Double.MAX_VALUE

        // 모든 보간된 경로 세그먼트에 대해 매칭 후보를 생성하고 평가
        for (segmentIndex in 0 until interpolatedPoints.size - 1) {
            val startPoint = interpolatedPoints[segmentIndex]
            val endPoint = interpolatedPoints[segmentIndex + 1]

            // 현재 세그먼트에서 GPS 좌표에 대한 최적 매칭점 탐색
            val matchCandidate = findBestMatchOnInterpolatedSegment(
                gpsLat,
                gpsLon,
                bearing,
                startPoint,
                endPoint
            )

            // 매칭 후보의 품질을 나타내는 종합 점수 계산
            val matchScore = calculateMatchScore(matchCandidate, bearing)

            // 현재까지의 최적 매칭보다 더 좋은 점수인 경우 업데이트
            if (matchScore < minScore) {
                minScore = matchScore
                bestMatch = InterpolatedRouteMatch(
                    matchCandidate,
                    segmentIndex,
                    startPoint.distanceFromStart + matchCandidate.distanceAlongSegment
                )
            }
        }

        // 최적 매칭이 발견된 경우 최종 결과 생성
        bestMatch?.let { match ->
            // 거리 임계값을 기준으로 매칭 성공 여부 판정
            val isMatched = match.candidate.distanceToSegment <= MAX_DISTANCE_THRESHOLD
            
            // 전체 경로 길이 계산 (마지막 포인트의 누적 거리)
            val totalDistance = interpolatedPoints.lastOrNull()?.distanceFromStart ?: 0.0
            
            // 전체 경로에서의 진행률 계산 (0.0 ~ 1.0 범위)
            val routeProgress = if (totalDistance > 0) match.progressDistance / totalDistance else 0.0

            return MatchResult(
                matched = isMatched,
                distanceToRoute = match.candidate.distanceToSegment,
                // 매칭 성공 시 보정된 좌표, 실패 시 원본 GPS 좌표 사용
                matchedLatitude = if (isMatched) match.candidate.projectedLat else gpsLat,
                matchedLongitude = if (isMatched) match.candidate.projectedLon else gpsLon,
                routeProgress = routeProgress
            )
        }

        // 매칭할 수 있는 세그먼트가 없는 경우 기본 결과 반환
        return MatchResult(
            matched = false,
            distanceToRoute = Double.MAX_VALUE,
            matchedLatitude = gpsLat,
            matchedLongitude = gpsLon,
            routeProgress = 0.0
        )
    }

    /**
     * 보간된 경로 세그먼트에서 GPS 좌표에 대한 최적 매칭점을 찾는 함수
     */
    private fun findBestMatchOnInterpolatedSegment(
        gpsLat: Double,
        gpsLon: Double,
        bearing: Double,
        startPoint: InterpolatedPoint,
        endPoint: InterpolatedPoint
    ): MatchCandidate {
        // GPS 좌표를 선분에 수직으로 투영하여 가장 가까운 점 계산
        val projectedPoint = projectPointToLineSegment(
            gpsLat,
            gpsLon,
            startPoint.latitude,
            startPoint.longitude,
            endPoint.latitude,
            endPoint.longitude
        )

        // GPS 원본 위치에서 투영된 점까지의 실제 거리 계산
        val distanceToSegment = GeoUtils.calculateDistance(
            gpsLat,
            gpsLon,
            projectedPoint.first,
            projectedPoint.second
        )

        // 현재 세그먼트의 방향 계산 (북쪽 기준 시계방향 각도)
        val segmentBearing = GeoUtils.calculateBearing(
            startPoint.latitude,
            startPoint.longitude,
            endPoint.latitude,
            endPoint.longitude
        )

        // 현재 이동 방향과 세그먼트 방향 사이의 각도 차이 계산
        val bearingDifference = calculateBearingDifference(bearing, segmentBearing)

        // 세그먼트 시작점에서 투영된 점까지의 거리 계산
        val distanceFromStartOfSegment = GeoUtils.calculateDistance(
            startPoint.latitude,
            startPoint.longitude,
            projectedPoint.first,
            projectedPoint.second
        )

        return MatchCandidate(
            projectedLat = projectedPoint.first,
            projectedLon = projectedPoint.second,
            distanceToSegment = distanceToSegment,
            bearingDifference = bearingDifference,
            distanceAlongSegment = distanceFromStartOfSegment
        )
    }

    /**
     * 특정 경로 세그먼트에서 GPS 좌표에 대한 최적 매칭점을 찾는 함수
     * 
     * 이 함수는 주어진 선분(경로 세그먼트)에 GPS 좌표를 수직으로 투영하여
     * 가장 가까운 점을 찾고, 해당 지점의 매칭 품질을 평가합니다.
     * 
     * 계산 과정:
     * 1. GPS 좌표를 선분에 수직 투영하여 가장 가까운 점 계산
     * 2. 투영점과 GPS 좌표 사이의 거리 계산
     * 3. 현재 이동 방향과 세그먼트 방향의 차이 계산
     * 4. 세그먼트 내에서의 진행 거리 계산
     * 
     * @param gpsLat 현재 GPS 위도
     * @param gpsLon 현재 GPS 경도
     * @param bearing 현재 이동 방향
     * @param startLat 세그먼트 시작점의 위도
     * @param startLon 세그먼트 시작점의 경도
     * @param endLat 세그먼트 끝점의 위도
     * @param endLon 세그먼트 끝점의 경도
     * @return 매칭 후보 정보를 포함하는 MatchCandidate 객체
     */
    private fun findBestMatchOnSegment(
        gpsLat: Double,
        gpsLon: Double,
        bearing: Double,
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double
    ): MatchCandidate {
        // GPS 좌표를 선분에 수직으로 투영하여 가장 가까운 점 계산
        // 이는 기하학적으로 점과 선분 사이의 최단 거리를 구하는 과정
        val projectedPoint = projectPointToLineSegment(
            gpsLat,
            gpsLon,
            startLat,
            startLon,
            endLat,
            endLon
        )

        // GPS 원본 위치에서 투영된 점까지의 실제 거리 계산 (미터 단위)
        // 이 거리가 작을수록 GPS 위치가 경로에 더 가깝다는 의미
        val distanceToSegment = GeoUtils.calculateDistance(
            gpsLat,
            gpsLon,
            projectedPoint.first,
            projectedPoint.second
        )

        // 현재 세그먼트의 방향(베어링) 계산
        // 시작점에서 끝점을 향하는 방향을 북쪽 기준 시계방향 각도로 표현
        val segmentBearing = GeoUtils.calculateBearing(
            startLat,
            startLon,
            endLat,
            endLon
        )

        // 현재 이동 방향과 세그먼트 방향 사이의 각도 차이 계산
        // 방향이 비슷할수록 올바른 경로를 따라 이동하고 있다고 판단
        val bearingDifference = calculateBearingDifference(bearing, segmentBearing)

        // 현재 세그먼트의 전체 길이 계산 (참조용, 현재는 사용되지 않음)
        val segmentLength = GeoUtils.calculateDistance(startLat, startLon, endLat, endLon)
        
        // 세그먼트 시작점에서 투영된 점까지의 거리 계산
        // 이는 해당 세그먼트 내에서 얼마나 진행했는지를 나타냄
        val distanceFromStartOfSegment = GeoUtils.calculateDistance(
            startLat,
            startLon,
            projectedPoint.first,
            projectedPoint.second
        )

        // 모든 계산 결과를 포함하는 매칭 후보 객체 생성
        return MatchCandidate(
            projectedLat = projectedPoint.first, // 투영된 지점의 위도
            projectedLon = projectedPoint.second, // 투영된 지점의 경도
            distanceToSegment = distanceToSegment, // GPS 위치에서 세그먼트까지의 거리
            bearingDifference = bearingDifference, // 방향 차이 (도 단위)
            distanceAlongSegment = distanceFromStartOfSegment // 세그먼트 내 진행 거리
        )
    }

    /**
     * 점을 선분에 수직으로 투영하는 기하학적 계산 함수
     * 
     * 이 함수는 3차원 공간에서 점을 선분에 투영하는 벡터 기하학을 사용합니다.
     * GPS 좌표를 경로 세그먼트의 가장 가까운 지점으로 보정하는 핵심 계산을 수행합니다.
     * 
     * 수학적 배경:
     * - 벡터 투영 공식을 사용하여 점 P를 선분 AB에 투영
     * - 투영 매개변수 t를 계산하여 투영점의 위치 결정
     * - t < 0: 시작점 A에 가장 가까움
     * - t > 1: 끝점 B에 가장 가까움  
     * - 0 ≤ t ≤ 1: 선분 AB 사이의 실제 투영점
     * 
     * @param pointLat 투영할 점의 위도
     * @param pointLon 투영할 점의 경도
     * @param line1Lat 선분 시작점의 위도
     * @param line1Lon 선분 시작점의 경도
     * @param line2Lat 선분 끝점의 위도
     * @param line2Lon 선분 끝점의 경도
     * @return 투영된 점의 좌표 (위도, 경도)
     */
    private fun projectPointToLineSegment(
        pointLat: Double,
        pointLon: Double,
        line1Lat: Double,
        line1Lon: Double,
        line2Lat: Double,
        line2Lon: Double
    ): Pair<Double, Double> {
        // 점에서 선분 시작점으로의 벡터 성분 계산
        // 이는 투영 계산의 기준이 되는 상대적 위치 벡터
        val pointToLineStartLat = pointLat - line1Lat
        val pointToLineStartLon = pointLon - line1Lon
        
        // 선분의 방향 벡터 성분 계산
        // 시작점에서 끝점으로의 방향과 크기를 나타내는 벡터
        val lineDirectionLat = line2Lat - line1Lat
        val lineDirectionLon = line2Lon - line1Lon

        // 두 벡터의 내적(dot product) 계산
        // 기하학적으로는 한 벡터를 다른 벡터에 투영했을 때의 스칼라 값
        val dotProduct = pointToLineStartLat * lineDirectionLat + pointToLineStartLon * lineDirectionLon
        
        // 선분 방향 벡터의 크기의 제곱
        // 정규화를 위해 사용되는 값
        val lineDirectionMagnitudeSquared = lineDirectionLat * lineDirectionLat + lineDirectionLon * lineDirectionLon

        // 선분의 길이가 0인 경우 (시작점과 끝점이 같은 경우)
        // 투영할 수 없으므로 시작점을 그대로 반환
        if (lineDirectionMagnitudeSquared == 0.0) {
            return Pair(line1Lat, line1Lon)
        }

        // 투영 매개변수 계산
        // 0이면 시작점, 1이면 끝점, 0.5면 중점에 해당
        val projectionParameter = dotProduct / lineDirectionMagnitudeSquared

        // 매개변수 값에 따라 최종 투영점 결정
        return when {
            // 매개변수가 0보다 작으면 시작점이 가장 가까운 지점
            projectionParameter < 0 -> Pair(line1Lat, line1Lon)
            // 매개변수가 1보다 크면 끝점이 가장 가까운 지점
            projectionParameter > 1 -> Pair(line2Lat, line2Lon)
            // 0과 1 사이면 선분 위의 실제 투영점 계산
            else -> Pair(line1Lat + projectionParameter * lineDirectionLat, line1Lon + projectionParameter * lineDirectionLon)
        }
    }

    /**
     * 매칭 후보의 종합적인 품질 점수를 계산하는 함수
     * 
     * 이 함수는 거리와 방향 두 가지 요소를 가중평균하여 하나의 점수로 통합합니다.
     * 점수가 낮을수록 더 좋은 매칭을 의미하며, 0에 가까울수록 완벽한 매칭입니다.
     * 
     * 평가 기준:
     * 1. 거리 점수 (60% 가중치): GPS 위치와 경로 사이의 물리적 거리
     * 2. 방향 점수 (40% 가중치): 현재 이동 방향과 경로 방향의 일치도
     * 
     * 정규화 과정:
     * - 거리: 최대 임계값으로 나누어 0~1 범위로 정규화
     * - 방향: 180도로 나누어 0~1 범위로 정규화
     * 
     * @param candidate 평가할 매칭 후보
     * @param bearing 현재 이동 방향
     * @return 0에 가까울수록 좋은 종합 점수
     */
    private fun calculateMatchScore(candidate: MatchCandidate, bearing: Double): Double {
        // 거리를 0~1 범위로 정규화
        // 임계값 이내의 거리는 1 이하의 값을, 임계값을 초과하면 1 이상의 값을 가짐
        val normalizedDistanceScore = candidate.distanceToSegment / MAX_DISTANCE_THRESHOLD
        
        // 방향 차이를 0~1 범위로 정규화  
        // 180도 차이(정반대 방향)를 최대값 1로 설정
        val normalizedBearingScore = candidate.bearingDifference / 180.0

        // 가중평균으로 최종 종합 점수 계산
        // 거리를 더 중요하게 여기므로 60% 가중치 적용
        return WEIGHT_DISTANCE * normalizedDistanceScore + WEIGHT_BEARING * normalizedBearingScore
    }

    /**
     * 두 방향(베어링) 사이의 최소 각도 차이를 계산하는 함수
     * 
     * 방향은 원형 좌표계(0-360도)를 사용하므로 일반적인 뺄셈으로는 
     * 올바른 차이를 계산할 수 없습니다. 예를 들어, 359도와 1도의 차이는
     * 358도가 아니라 2도입니다.
     * 
     * 이 함수는 두 방향 사이의 최단 각도 차이를 계산하여
     * 항상 0도에서 180도 사이의 값을 반환합니다.
     * 
     * @param bearing1 첫 번째 방향 (0-360도)
     * @param bearing2 두 번째 방향 (0-360도)
     * @return 두 방향 사이의 최소 각도 차이 (0-180도)
     */
    private fun calculateBearingDifference(bearing1: Double, bearing2: Double): Double {
        // 두 방향의 절댓값 차이 계산
        var angleDifference = kotlin.math.abs(bearing1 - bearing2)
        
        // 180도보다 큰 경우, 반대 방향으로 계산하는 것이 더 짧은 거리
        // 원형 좌표계의 특성상 시계방향과 반시계방향 중 더 짧은 경로 선택
        if (angleDifference > 180) {
            angleDifference = 360 - angleDifference
        }
        
        return angleDifference
    }
}

/**
 * 경로 매칭 후보의 상세 정보를 담는 데이터 클래스
 * 
 * 특정 경로 세그먼트에 대한 GPS 좌표의 매칭 결과와
 * 매칭 품질을 평가하기 위한 모든 필요한 정보를 포함합니다.
 */
data class MatchCandidate(
    val projectedLat: Double, // 경로 세그먼트에 투영된 지점의 위도
    val projectedLon: Double, // 경로 세그먼트에 투영된 지점의 경도
    val distanceToSegment: Double, // 원본 GPS 위치에서 투영 지점까지의 거리 (미터)
    val bearingDifference: Double, // 이동 방향과 세그먼트 방향의 차이 (도)
    val distanceAlongSegment: Double // 세그먼트 시작점에서 투영 지점까지의 거리 (미터)
)

/**
 * 특정 세그먼트에서의 매칭 결과를 담는 데이터 클래스
 * 
 * 매칭 후보 정보와 함께 해당 세그먼트의 위치 정보 및
 * 전체 경로에서의 진행 상황을 포함합니다.
 */
data class RouteMatch(
    val candidate: MatchCandidate, // 매칭 후보의 상세 정보
    val segmentIndex: Int, // 매칭된 세그먼트의 인덱스 (0부터 시작)
    val progressDistance: Double // 전체 경로 시작점에서부터의 누적 거리 (미터)
)

/**
 * 보간된 경로에서의 매칭 결과를 담는 데이터 클래스
 */
data class InterpolatedRouteMatch(
    val candidate: MatchCandidate, // 매칭 후보의 상세 정보
    val segmentIndex: Int, // 매칭된 세그먼트의 인덱스 (0부터 시작)
    val progressDistance: Double // 전체 경로 시작점에서부터의 누적 거리 (미터)
)

/**
 * 최종 경로 매칭 결과를 담는 데이터 클래스
 * 
 * GPS 좌표의 경로 매칭 과정을 거쳐 산출된 최종 결과로,
 * 매칭 성공 여부, 보정된 좌표, 그리고 경로 진행률 등의
 * 모든 중요한 정보를 포함합니다.
 */
data class MatchResult(
    val matched: Boolean, // 매칭 성공 여부 (임계값 내 거리인지 판정)
    val distanceToRoute: Double, // 원본 GPS 위치에서 가장 가까운 경로 지점까지의 거리 (미터)
    val matchedLatitude: Double, // 최종 매칭된 위도 (보정된 좌표 또는 원본 좌표)
    val matchedLongitude: Double, // 최종 매칭된 경도 (보정된 좌표 또는 원본 좌표)
    val routeProgress: Double // 전체 경로에서의 진행률 (0.0 = 시작, 1.0 = 완료)
) 

package com.sponovation.runtrack.service

import com.sponovation.runtrack.domain.Checkpoint
import com.sponovation.runtrack.domain.GpxRoute
import com.sponovation.runtrack.domain.GpxWaypoint
import com.sponovation.runtrack.repository.CheckpointRepository
import com.sponovation.runtrack.repository.GpxRouteRepository
import com.sponovation.runtrack.repository.GpxWaypointRepository
import com.sponovation.runtrack.util.GeoUtils
import io.jenetics.jpx.GPX
import io.jenetics.jpx.Track
import io.jenetics.jpx.WayPoint
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDateTime
import com.sponovation.runtrack.dto.CorrectLocationRequestDto
import com.sponovation.runtrack.dto.CorrectLocationResponseDto
import com.sponovation.runtrack.dto.CorrectedLocationDataDto
import com.sponovation.runtrack.dto.GpxUploadResult
import com.sponovation.runtrack.algorithm.KalmanFilter
import com.sponovation.runtrack.algorithm.MapMatcher
import java.util.NoSuchElementException


/**
 * GPX 파일 처리 및 경로 관리 서비스
 * 
 * GPX(GPS Exchange Format) 파일을 파싱하여 경로 데이터를 추출하고,
 * 웨이포인트와 체크포인트를 자동 생성하여 데이터베이스에 저장하는 서비스
 * 
 * 주요 기능:
 * - GPX 파일 파싱 및 데이터 추출
 * - 경로 통계 계산 (거리, 고도 변화)
 * - 웨이포인트 자동 생성 및 저장
 * - 체크포인트 자동 생성 (1km 간격)
 * - 경로 데이터 조회 기능
 */
@Service
@Transactional
class GpxService(
    private val gpxRouteRepository: GpxRouteRepository,
    private val gpxWaypointRepository: GpxWaypointRepository,
    private val checkpointRepository: CheckpointRepository,
    private val realtimeLocationService: RealtimeLocationService,
    private val courseDataService: CourseDataService
) {
    
    companion object {
        private val logger = LoggerFactory.getLogger(GpxService::class.java)
    }

    /**
     * GPX 파일을 파싱하여 DB와 Redis에 통합 저장 (최적화된 단일 파싱)
     * 
     * 이 메서드는 GPX 파일을 한 번만 파싱하여 다음 작업을 수행합니다:
     * 1. GPX 파일 파싱 및 100미터 간격 보간
     * 2. 데이터베이스에 GpxRoute 및 GpxWaypoint 저장
     * 3. Redis에 CourseData 저장 (실시간 위치 보정용)
     * 4. 체크포인트 자동 생성
     * 
     * @param file 업로드된 GPX 파일
     * @param routeName 경로명
     * @param description 경로 설명
     * @param eventDetailId 이벤트 상세 ID (Redis 저장용)
     * @return 통합 업로드 결과
     */
    fun parseAndSaveGpxFileWithCourseData(
        file: MultipartFile, 
        routeName: String, 
        description: String,
        eventDetailId: Long
    ): GpxUploadResult {
        logger.info("GPX 파일 통합 처리 시작: 파일명={}, 크기={} bytes, 경로명={}, 이벤트ID={}", 
            file.originalFilename, file.size, routeName, eventDetailId)
        
        val startTime = System.currentTimeMillis()
        
        // 파일 크기 및 형식 검증
        if (file.isEmpty) {
            logger.error("빈 파일 업로드 시도: 파일명={}", file.originalFilename)
            throw IllegalArgumentException("업로드된 파일이 비어있습니다")
        }
        
        if (file.size > 10 * 1024 * 1024) { // 10MB 제한
            logger.error("파일 크기 초과: 파일명={}, 크기={} bytes", file.originalFilename, file.size)
            throw IllegalArgumentException("파일 크기가 너무 큽니다 (최대 10MB)")
        }

        logger.debug("파일 검증 완료: 파일명={}", file.originalFilename)

        // 임시 파일 생성 (GPX 라이브러리가 파일 경로를 필요로 하기 때문)
        val tempFile = try {
            java.io.File.createTempFile("gpx", ".gpx")
        } catch (e: Exception) {
            logger.error("임시 파일 생성 실패: {}", e.message, e)
            throw RuntimeException("임시 파일 생성에 실패했습니다: ${e.message}", e)
        }

        logger.debug("임시 파일 생성 완료: {}", tempFile.absolutePath)

        try {
            // 업로드된 파일을 임시 파일로 복사
            file.transferTo(tempFile)
            logger.debug("파일 복사 완료: {} -> {}", file.originalFilename, tempFile.absolutePath)
            
            // GPX 파일 파싱
            val gpx = try {
                logger.debug("GPX 파일 파싱 시작")
                GPX.read(tempFile.toPath())
            } catch (e: Exception) {
                logger.error("GPX 파일 파싱 실패: 파일명={}, 오류={}", file.originalFilename, e.message, e)
                throw IllegalArgumentException("GPX 파일 파싱에 실패했습니다. 올바른 GPX 형식인지 확인해주세요: ${e.message}", e)
            }

            logger.debug("GPX 파일 파싱 완료")

            // 트랙 데이터 검증
            val tracks = gpx.tracks.toList()
            if (tracks.isEmpty()) {
                logger.error("트랙 데이터 없음: 파일명={}", file.originalFilename)
                throw IllegalArgumentException("GPX 파일에 트랙 데이터가 없습니다")
            }

            logger.debug("트랙 개수: {}", tracks.size)

            // 첫 번째 트랙 선택
            val track = tracks.first()
            val segments = track.segments.toList()
            if (segments.isEmpty()) {
                logger.error("세그먼트 데이터 없음: 파일명={}", file.originalFilename)
                throw IllegalArgumentException("GPX 파일에 세그먼트 데이터가 없습니다")
            }

            logger.debug("세그먼트 개수: {}", segments.size)

            // 트랙에서 웨이포인트 추출 (100미터 간격 보간 포함)
            val waypoints = extractWaypointsFromTrack(track)
            logger.info("웨이포인트 추출 완료: {}개 (보간 포함)", waypoints.size)
            
            // 웨이포인트 최소 개수 검증
            if (waypoints.size < 2) {
                logger.error("웨이포인트 개수 부족: {}개 (최소 2개 필요)", waypoints.size)
                throw IllegalArgumentException("경로를 생성하기 위해서는 최소 2개 이상의 웨이포인트가 필요합니다 (현재: ${waypoints.size}개)")
            }
            
            // 경로 통계 계산 (총 거리, 고도 변화 등)
            logger.debug("경로 통계 계산 시작")
            val routeStats = calculateRouteStatistics(waypoints)
            logger.info("경로 통계 계산 완료: 총거리={:.1f}m, 고도상승={:.1f}m, 고도하강={:.1f}m", 
                routeStats.totalDistance, routeStats.elevationGain, routeStats.elevationLoss)

            // 최소 거리 검증
            if (routeStats.totalDistance < 10.0) { // 10미터 미만
                logger.error("경로 거리 부족: {:.1f}m (최소 10m 필요)", routeStats.totalDistance)
                throw IllegalArgumentException("경로가 너무 짧습니다 (최소 10m 이상 필요, 현재: ${String.format("%.1f", routeStats.totalDistance)}m)")
            }

            // 1. GPX 경로 엔티티 생성 및 저장
            logger.debug("GPX 경로 엔티티 저장 시작")
            val gpxRoute = gpxRouteRepository.save(
                GpxRoute(
                    name = routeName,
                    description = description,
                    totalDistance = routeStats.totalDistance,
                    totalElevationGain = routeStats.elevationGain,
                    totalElevationLoss = routeStats.elevationLoss,
                    createdAt = LocalDateTime.now()
                )
            )
            logger.info("GPX 경로 저장 완료: ID={}, 이름={}", gpxRoute.id, gpxRoute.name)

            // 2. 웨이포인트 데이터를 DB에 저장
            logger.debug("웨이포인트 저장 시작: {}개", waypoints.size)
            saveWaypoints(gpxRoute, waypoints)
            logger.info("웨이포인트 저장 완료: {}개", waypoints.size)

            // 3. 자동 체크포인트 생성
            logger.debug("체크포인트 자동 생성 시작")
            generateAutoCheckpoints(gpxRoute, waypoints)
            
            // 4. Redis에 코스 데이터 저장 (파싱된 웨이포인트 재사용)
            logger.debug("Redis에 코스 데이터 저장 시작")
            val courseId = courseDataService.saveFromParsedWaypoints(
                eventId = eventDetailId,
                fileName = file.originalFilename ?: "unknown.gpx",
                waypoints = waypoints
            )
            logger.info("Redis 코스 데이터 저장 완료: courseId={}", courseId)
            
            val processingTime = System.currentTimeMillis() - startTime
            logger.info("GPX 파일 통합 처리 완료: 처리시간={}ms, 경로ID={}, 코스ID={}, 웨이포인트={}개, 총거리={:.1f}m", 
                processingTime, gpxRoute.id, courseId, waypoints.size, routeStats.totalDistance)
            
            return GpxUploadResult(
                gpxRoute = gpxRoute,
                courseId = courseId,
                totalInterpolatedPoints = waypoints.size,
                createdAt = LocalDateTime.now().toString()
            )
            
        } catch (e: IllegalArgumentException) {
            logger.warn("GPX 파일 처리 실패 (검증 오류): {}", e.message)
            throw e
        } catch (e: Exception) {
            logger.error("GPX 파일 처리 중 예상치 못한 오류 발생: {}", e.message, e)
            throw RuntimeException("GPX 파일 처리 중 오류가 발생했습니다: ${e.message}", e)
        } finally {
            // 임시 파일 정리
            try {
                if (tempFile.exists()) {
                    val deleted = tempFile.delete()
                    if (deleted) {
                        logger.debug("임시 파일 삭제 완료: {}", tempFile.absolutePath)
                    } else {
                        logger.warn("임시 파일 삭제 실패: {}", tempFile.absolutePath)
                    }
                }
            } catch (e: Exception) {
                logger.warn("임시 파일 삭제 중 오류 발생: {}", e.message)
            }
        }
    }

    /**
     * GPX 파일을 파싱하여 경로 데이터를 저장
     * 
     * @param file 업로드된 GPX 파일
     * @param routeName 경로명
     * @param description 경로 설명
     * @return 저장된 GpxRoute 엔티티
     * @throws IllegalArgumentException GPX 파일에 트랙 데이터가 없는 경우
     */
    fun parseAndSaveGpxFile(file: MultipartFile, routeName: String, description: String): GpxRoute {
        logger.info("GPX 파일 파싱 시작: 파일명={}, 크기={} bytes, 경로명={}", 
            file.originalFilename, file.size, routeName)
        
        val startTime = System.currentTimeMillis()
        
        // 파일 크기 및 형식 검증
        if (file.isEmpty) {
            logger.error("빈 파일 업로드 시도: 파일명={}", file.originalFilename)
            throw IllegalArgumentException("업로드된 파일이 비어있습니다")
        }
        
        if (file.size > 10 * 1024 * 1024) { // 10MB 제한
            logger.error("파일 크기 초과: 파일명={}, 크기={} bytes", file.originalFilename, file.size)
            throw IllegalArgumentException("파일 크기가 너무 큽니다 (최대 10MB)")
        }

        logger.debug("파일 검증 완료: 파일명={}", file.originalFilename)

        // 임시 파일 생성 (GPX 라이브러리가 파일 경로를 필요로 하기 때문)
        val tempFile = try {
            java.io.File.createTempFile("gpx", ".gpx")
        } catch (e: Exception) {
            logger.error("임시 파일 생성 실패: {}", e.message, e)
            throw RuntimeException("임시 파일 생성에 실패했습니다: ${e.message}", e)
        }

        logger.debug("임시 파일 생성 완료: {}", tempFile.absolutePath)

        try {
            // 업로드된 파일을 임시 파일로 복사
            file.transferTo(tempFile)
            logger.debug("파일 복사 완료: {} -> {}", file.originalFilename, tempFile.absolutePath)
            
            // GPX 파일 파싱
            val gpx = try {
                logger.debug("GPX 파일 파싱 시작")
                GPX.read(tempFile.toPath())
            } catch (e: Exception) {
                logger.error("GPX 파일 파싱 실패: 파일명={}, 오류={}", file.originalFilename, e.message, e)
                throw IllegalArgumentException("GPX 파일 파싱에 실패했습니다. 올바른 GPX 형식인지 확인해주세요: ${e.message}", e)
            }

            logger.debug("GPX 파일 파싱 완료")

            // 트랙 데이터 검증
            val tracks = gpx.tracks.toList()
            if (tracks.isEmpty()) {
                logger.error("트랙 데이터 없음: 파일명={}", file.originalFilename)
                throw IllegalArgumentException("GPX 파일에 트랙 데이터가 없습니다")
            }

            logger.debug("트랙 개수: {}", tracks.size)

            // 첫 번째 트랙 선택 (일반적으로 GPX 파일은 하나의 트랙을 포함)
            val track = tracks.first()
            val segments = track.segments.toList()
            if (segments.isEmpty()) {
                logger.error("세그먼트 데이터 없음: 파일명={}", file.originalFilename)
                throw IllegalArgumentException("GPX 파일에 세그먼트 데이터가 없습니다")
            }

            logger.debug("세그먼트 개수: {}", segments.size)

            // 트랙에서 웨이포인트 추출
            val waypoints = extractWaypointsFromTrack(track)
            logger.info("웨이포인트 추출 완료: {}개", waypoints.size)
            
            // 웨이포인트 최소 개수 검증
            if (waypoints.size < 2) {
                logger.error("웨이포인트 개수 부족: {}개 (최소 2개 필요)", waypoints.size)
                throw IllegalArgumentException("경로를 생성하기 위해서는 최소 2개 이상의 웨이포인트가 필요합니다 (현재: ${waypoints.size}개)")
            }
            
            // 경로 통계 계산 (총 거리, 고도 변화 등)
            logger.debug("경로 통계 계산 시작")
            val routeStats = calculateRouteStatistics(waypoints)
            logger.info("경로 통계 계산 완료: 총거리={:.1f}m, 고도상승={:.1f}m, 고도하강={:.1f}m", 
                routeStats.totalDistance, routeStats.elevationGain, routeStats.elevationLoss)

            // 최소 거리 검증 (너무 짧은 경로 방지)
            if (routeStats.totalDistance < 10.0) { // 10미터 미만
                logger.error("경로 거리 부족: {:.1f}m (최소 10m 필요)", routeStats.totalDistance)
                throw IllegalArgumentException("경로가 너무 짧습니다 (최소 10m 이상 필요, 현재: ${String.format("%.1f", routeStats.totalDistance)}m)")
            }

            // GPX 경로 엔티티 생성 및 저장
            logger.debug("GPX 경로 엔티티 저장 시작")
            val gpxRoute = gpxRouteRepository.save(
                GpxRoute(
                    name = routeName,
                    description = description,
                    totalDistance = routeStats.totalDistance,
                    totalElevationGain = routeStats.elevationGain,
                    totalElevationLoss = routeStats.elevationLoss,
                    createdAt = LocalDateTime.now()
                )
            )
            logger.info("GPX 경로 저장 완료: ID={}, 이름={}", gpxRoute.id, gpxRoute.name)

            // 웨이포인트 데이터 저장
            logger.debug("웨이포인트 저장 시작: {}개", waypoints.size)
            saveWaypoints(gpxRoute, waypoints)
            logger.info("웨이포인트 저장 완료: {}개", waypoints.size)

            // 자동으로 체크포인트 생성 (1km 간격으로 거리 기반)
            logger.debug("체크포인트 자동 생성 시작")
            generateAutoCheckpoints(gpxRoute, waypoints)
            
            val processingTime = System.currentTimeMillis() - startTime
            logger.info("GPX 파일 파싱 완료: 처리시간={}ms, 경로ID={}, 웨이포인트={}개, 총거리={:.1f}m", 
                processingTime, gpxRoute.id, waypoints.size, routeStats.totalDistance)
            
            return gpxRoute
        } catch (e: IllegalArgumentException) {
            // 비즈니스 로직 예외는 그대로 전파
            logger.warn("GPX 파일 처리 실패 (검증 오류): {}", e.message)
            throw e
        } catch (e: Exception) {
            // 예상하지 못한 예외 처리
            logger.error("GPX 파일 처리 중 예상치 못한 오류 발생: {}", e.message, e)
            throw RuntimeException("GPX 파일 처리 중 오류가 발생했습니다: ${e.message}", e)
        } finally {
            // 임시 파일 정리 (파일이 존재하고 삭제할 수 있는 경우만)
            try {
                if (tempFile.exists()) {
                    val deleted = tempFile.delete()
                    if (deleted) {
                        logger.debug("임시 파일 삭제 완료: {}", tempFile.absolutePath)
                    } else {
                        logger.warn("임시 파일 삭제 실패: {}", tempFile.absolutePath)
                    }
                }
            } catch (e: Exception) {
                // 임시 파일 삭제 실패는 로그만 남기고 무시
                logger.warn("임시 파일 삭제 중 오류 발생: {}", e.message)
            }
        }
    }

    /**
     * GPX 트랙에서 모든 웨이포인트를 추출하고 100미터 간격으로 보간 포인트 추가
     * 
     * GPX 트랙은 여러 세그먼트로 구성될 수 있으며,
     * 각 세그먼트는 연속된 웨이포인트들을 포함합니다.
     * 두 웨이포인트 사이의 거리가 100미터를 초과하는 경우 중간에 보간 포인트를 추가합니다.
     * 
     * @param track GPX 트랙 객체
     * @return 모든 웨이포인트의 리스트 (보간 포인트 포함)
     */
    private fun extractWaypointsFromTrack(track: Track): List<WayPoint> {
        val allWaypoints = mutableListOf<WayPoint>()
        val interpolationInterval = 100.0 // 100미터 간격

        logger.debug("웨이포인트 추출 시작: 세그먼트 개수={}", track.segments.count())

        // 모든 세그먼트를 순회하며 웨이포인트 수집 및 보간
        track.segments.forEach { segment ->
            val segmentPoints = segment.points.toList()
            logger.debug("세그먼트 처리 시작: 원본 포인트 개수={}", segmentPoints.size)
            
            if (segmentPoints.isNotEmpty()) {
                // 첫 번째 포인트 추가
                allWaypoints.add(segmentPoints.first())
                
                // 연속된 포인트들 사이에 보간 포인트 추가
                for (i in 1 until segmentPoints.size) {
                    val prevPoint = segmentPoints[i - 1]
                    val currPoint = segmentPoints[i]
                    
                    // 두 포인트 사이의 거리 계산
                    val distance = GeoUtils.calculateDistance(
                        prevPoint.latitude.toDegrees(),
                        prevPoint.longitude.toDegrees(),
                        currPoint.latitude.toDegrees(),
                        currPoint.longitude.toDegrees()
                    )
                    
                    // 거리가 100미터를 초과하는 경우 보간 포인트 추가
                    if (distance > interpolationInterval) {
                        val prevElevation = prevPoint.elevation.map { it.toDouble() }.orElse(null)
                        val currElevation = currPoint.elevation.map { it.toDouble() }.orElse(null)
                        
                        // 보간 포인트 생성
                        val interpolatedPoints = GeoUtils.generateInterpolatedPoints(
                            prevPoint.latitude.toDegrees(),
                            prevPoint.longitude.toDegrees(),
                            prevElevation,
                            currPoint.latitude.toDegrees(),
                            currPoint.longitude.toDegrees(),
                            currElevation,
                            interpolationInterval
                        )
                        
                        // 보간 포인트들을 WayPoint 객체로 변환하여 추가
                        interpolatedPoints.forEach { (lat, lon, elev) ->
                            val interpolatedWaypoint = WayPoint.builder()
                                .lat(lat)
                                .lon(lon)
                                .also { builder ->
                                    elev?.let { elevation ->
                                        builder.ele(elevation)
                                    }
                                }
                                .build()
                            allWaypoints.add(interpolatedWaypoint)
                        }
                        
                        logger.debug("보간 포인트 추가: 거리={:.1f}m, 추가된 포인트={}개", distance, interpolatedPoints.size)
                    }
                    
                    // 현재 포인트 추가
                    allWaypoints.add(currPoint)
                }
            }
        }

        logger.info("웨이포인트 추출 완료: 총 {}개 (보간 포인트 포함)", allWaypoints.size)
        return allWaypoints
    }

    /**
     * 경로 통계 계산
     * 
     * 웨이포인트 간의 거리를 계산하여 총 거리를 구하고,
     * 고도 변화를 분석하여 상승/하강 고도를 계산합니다.
     * 
     * @param waypoints 웨이포인트 리스트
     * @return 경로 통계 데이터 (총 거리, 고도 상승, 고도 하강)
     */
    private fun calculateRouteStatistics(waypoints: List<WayPoint>): RouteStatistics {
        var totalDistance = 0.0
        var elevationGain = 0.0
        var elevationLoss = 0.0
        var previousElevation: Double? = null

        // 연속된 웨이포인트 간의 거리와 고도 변화 계산
        for (i in 1 until waypoints.size) {
            val prev = waypoints[i - 1]
            val curr = waypoints[i]

            // 두 지점 간의 거리 계산 (하버사인 공식 사용)
            val distance = GeoUtils.calculateDistance(
                prev.latitude.toDegrees(),
                prev.longitude.toDegrees(),
                curr.latitude.toDegrees(),
                curr.longitude.toDegrees()
            )
            totalDistance += distance

            // 고도 변화 계산 (유효한 고도 데이터가 있는 경우만)
            val prevElev = prev.elevation.map { it.toDouble() }.orElse(null)
            val currElev = curr.elevation.map { it.toDouble() }.orElse(null)

            // 고도 데이터가 유효한 경우에만 고도 변화 계산 (음수 고도는 유효하지 않은 데이터로 처리)
            if (prevElev != null && currElev != null && prevElev >= 0 && currElev >= 0) {
                previousElevation?.let { validPrevElevation ->
                    if (validPrevElevation >= 0) {
                        val elevationDiff = currElev - validPrevElevation
                        if (elevationDiff > 0) {
                            // 상승 고도 누적
                            elevationGain += elevationDiff
                        } else {
                            // 하강 고도 누적 (절댓값)
                            elevationLoss += kotlin.math.abs(elevationDiff)
                        }
                    }
                }
                previousElevation = currElev
            }
        }

        return RouteStatistics(totalDistance, elevationGain, elevationLoss)
    }

    /**
     * 웨이포인트 데이터를 데이터베이스에 저장
     * 
     * 각 웨이포인트마다 시작점으로부터의 누적 거리를 계산하여
     * GpxWaypoint 엔티티로 변환 후 저장합니다.
     * 
     * @param gpxRoute 저장된 GPX 경로 엔티티
     * @param waypoints 웨이포인트 리스트
     */
    private fun saveWaypoints(gpxRoute: GpxRoute, waypoints: List<WayPoint>) {
        var cumulativeDistance = 0.0

        waypoints.forEachIndexed { index, waypoint ->
            // 첫 번째 웨이포인트가 아닌 경우 누적 거리 계산
            if (index > 0) {
                val prevWaypoint = waypoints[index - 1]
                val distance = GeoUtils.calculateDistance(
                    prevWaypoint.latitude.toDegrees(),
                    prevWaypoint.longitude.toDegrees(),
                    waypoint.latitude.toDegrees(),
                    waypoint.longitude.toDegrees()
                )
                cumulativeDistance += distance
            }

            // 고도 데이터 처리: 음수이거나 없는 경우 0.0으로 처리
            val elevation = waypoint.elevation.map { it.toDouble() }.orElse(0.0)
            val validElevation = if (elevation < 0) 0.0 else elevation

            // GpxWaypoint 엔티티 생성 및 저장
            val gpxWaypoint = GpxWaypoint(
                latitude = waypoint.latitude.toDegrees(),
                longitude = waypoint.longitude.toDegrees(),
                elevation = validElevation, // 유효한 고도 데이터만 저장
                sequence = index, // 웨이포인트 순서
                distanceFromStart = cumulativeDistance, // 시작점으로부터의 누적 거리
                gpxRoute = gpxRoute
            )

            gpxWaypointRepository.save(gpxWaypoint)
        }
    }

    /**
     * 자동 체크포인트 생성
     * 
     * 경로를 따라 1km 간격으로 체크포인트를 자동 생성합니다.
     * 시작점과 종료점은 별도로 생성하며, 중간 지점들은 거리 기반으로 생성됩니다.
     * 
     * @param gpxRoute 저장된 GPX 경로 엔티티
     * @param waypoints 웨이포인트 리스트
     */
    private fun generateAutoCheckpoints(gpxRoute: GpxRoute, waypoints: List<WayPoint>) {
        logger.debug("체크포인트 자동 생성 시작: 경로ID={}, 웨이포인트={}개", gpxRoute.id, waypoints.size)
        
        val checkpointInterval = 1000.0 // 체크포인트 간격: 1km
        var nextCheckpointDistance = checkpointInterval // 다음 체크포인트까지의 목표 거리
        var cumulativeDistance = 0.0 // 누적 거리
        var checkpointSequence = 1 // 체크포인트 순서 번호
        var generatedCheckpoints = 0

        // 시작점 체크포인트 생성
        val startWaypoint = waypoints.first()
        val startCheckpoint = checkpointRepository.save(
            Checkpoint(
                name = "시작점",
                latitude = startWaypoint.latitude.toDegrees(),
                longitude = startWaypoint.longitude.toDegrees(),
                radius = 50.0, // 체크포인트 인식 반경: 50m
                sequence = 0,
                description = "경로 시작점",
                gpxRoute = gpxRoute
            )
        )
        generatedCheckpoints++
        logger.debug("시작점 체크포인트 생성: ID={}, 좌표=({:.6f}, {:.6f})", 
            startCheckpoint.id, startCheckpoint.latitude, startCheckpoint.longitude)

        // 중간 체크포인트들 생성 (1km 간격)
        for (i in 1 until waypoints.size) {
            val prevWaypoint = waypoints[i - 1]
            val currWaypoint = waypoints[i]

            // 현재 세그먼트의 거리 계산
            val segmentDistance = GeoUtils.calculateDistance(
                prevWaypoint.latitude.toDegrees(),
                prevWaypoint.longitude.toDegrees(),
                currWaypoint.latitude.toDegrees(),
                currWaypoint.longitude.toDegrees()
            )

            cumulativeDistance += segmentDistance

            // 누적 거리가 다음 체크포인트 거리에 도달한 경우
            if (cumulativeDistance >= nextCheckpointDistance) {
                val checkpoint = checkpointRepository.save(
                    Checkpoint(
                        name = "체크포인트 $checkpointSequence",
                        latitude = currWaypoint.latitude.toDegrees(),
                        longitude = currWaypoint.longitude.toDegrees(),
                        radius = 50.0,
                        sequence = checkpointSequence,
                        description = "${kotlin.math.round(cumulativeDistance)}m 지점",
                        gpxRoute = gpxRoute
                    )
                )
                generatedCheckpoints++
                logger.debug("중간 체크포인트 생성: ID={}, 순서={}, 거리={:.0f}m, 좌표=({:.6f}, {:.6f})", 
                    checkpoint.id, checkpointSequence, cumulativeDistance, 
                    checkpoint.latitude, checkpoint.longitude)

                // 다음 체크포인트 목표 거리 설정
                nextCheckpointDistance += checkpointInterval
                checkpointSequence++
            }
        }

        // 종료점 체크포인트 생성
        val endWaypoint = waypoints.last()
        val endCheckpoint = checkpointRepository.save(
            Checkpoint(
                name = "종료점",
                latitude = endWaypoint.latitude.toDegrees(),
                longitude = endWaypoint.longitude.toDegrees(),
                radius = 50.0,
                sequence = checkpointSequence,
                description = "경로 종료점",
                gpxRoute = gpxRoute
            )
        )
        generatedCheckpoints++
        logger.debug("종료점 체크포인트 생성: ID={}, 좌표=({:.6f}, {:.6f})", 
            endCheckpoint.id, endCheckpoint.latitude, endCheckpoint.longitude)
        
        logger.info("체크포인트 자동 생성 완료: 경로ID={}, 총 {}개 생성 (시작점 + 중간점 {}개 + 종료점)", 
            gpxRoute.id, generatedCheckpoints, generatedCheckpoints - 2)
    }

    /**
     * 모든 GPX 경로 조회
     * 
     * 생성일 기준 내림차순으로 정렬된 모든 GPX 경로를 반환합니다.
     * 
     * @return GPX 경로 리스트 (최신순)
     */
    fun getAllRoutes(): List<GpxRoute> {
        logger.debug("모든 GPX 경로 조회 시작")
        val routes = gpxRouteRepository.findAllOrderByCreatedAtDesc()
        logger.info("GPX 경로 조회 완료: {}개 경로", routes.size)
        return routes
    }

    /**
     * GPX 경로 상세 조회
     * 
     * 주어진 ID에 해당하는 GPX 경로를 조회합니다.
     * 
     * @param routeId 경로 ID
     * @return GPX 경로 엔티티 (존재하지 않으면 null)
     */
    fun getRouteById(routeId: Long): GpxRoute? {
        logger.debug("GPX 경로 상세 조회: ID={}", routeId)
        val route = gpxRouteRepository.findById(routeId).orElse(null)
        if (route != null) {
            logger.debug("GPX 경로 조회 성공: ID={}, 이름={}", route.id, route.name)
        } else {
            logger.warn("GPX 경로 조회 실패: ID={}가 존재하지 않음", routeId)
        }
        return route
    }

    /**
     * 경로의 웨이포인트 조회
     * 
     * 특정 경로에 속한 모든 웨이포인트를 순서대로 조회합니다.
     * 
     * @param routeId 경로 ID
     * @return 웨이포인트 리스트 (순서별 정렬)
     */
    fun getRouteWaypoints(routeId: Long): List<GpxWaypoint> {
        logger.debug("경로 웨이포인트 조회: 경로ID={}", routeId)
        val waypoints = gpxWaypointRepository.findByGpxRouteIdOrderBySequenceAsc(routeId)
        logger.debug("웨이포인트 조회 완료: 경로ID={}, {}개", routeId, waypoints.size)
        return waypoints
    }

    /**
     * 경로의 체크포인트 조회
     * 
     * 특정 경로에 속한 모든 체크포인트를 순서대로 조회합니다.
     * 
     * @param routeId 경로 ID
     * @return 체크포인트 리스트 (순서별 정렬)
     */
    fun getRouteCheckpoints(routeId: Long): List<Checkpoint> {
        logger.debug("경로 체크포인트 조회: 경로ID={}", routeId)
        val checkpoints = checkpointRepository.findByGpxRouteIdOrderBySequenceAsc(routeId)
        logger.debug("체크포인트 조회 완료: 경로ID={}, {}개", routeId, checkpoints.size)
        return checkpoints
    }

    @Transactional(readOnly = true)
    fun correctLocation(request: CorrectLocationRequestDto): CorrectLocationResponseDto {
        logger.info("위치 보정 요청: userId=${request.userId}, eventDetailId=${request.eventDetailId}, GPS 포인트 수=${request.gpsData.size}")
        
        try {
            val userId = request.userId
            val eventDetailId = request.eventDetailId
            val gpsDataList = request.gpsData
            
            // GPS 데이터 리스트 유효성 검증
            if (gpsDataList.isEmpty()) {
                throw IllegalArgumentException("GPS 데이터가 비어있습니다")
            }
            
            // Redis에서 코스 데이터 조회 (없으면 자동 로드)
            val courseData = realtimeLocationService.courseDataService.getCourseDataByEventId(eventDetailId)
            
            // 칼만 필터 초기화
            val kalman = KalmanFilter()
            
            // 가장 최신(마지막) GPS 포인트로 최종 결과 계산
            val lastGpsData = gpsDataList.last()
            var finalCorrectedLat = 0.0
            var finalCorrectedLng = 0.0
            
            // 각 GPS 포인트에 대해 순차 처리 (칼만 필터 상태 업데이트용)
            gpsDataList.forEachIndexed { index, gpsData ->
                val lat = gpsData.lat
                val lng = gpsData.lng
                val timestamp = gpsData.timestamp
                
                logger.debug("GPS 포인트 $index 처리: 원본위치=($lat, $lng)")
                
                // 칼만 필터를 사용한 GPS 노이즈 제거
                val filtered = kalman.filter(lat, lng)
                logger.debug("칼만 필터 적용: 원본=($lat, $lng) -> 필터링=(${"%.6f".format(filtered.first)}, ${"%.6f".format(filtered.second)})")
                
                // 마지막 포인트에 대해서만 경로 매칭 수행
                if (index == gpsDataList.size - 1) {
                    val (correctedLat, correctedLng) = if (courseData == null) {
                        // 코스 데이터가 없으면 기본 GPX Route에서 웨이포인트 사용
                        logger.warn("코스 데이터가 Redis에 없음. 기본 GPX Route 사용: eventDetailId=$eventDetailId")
                        val gpxRoute: GpxRoute = gpxRouteRepository.findById(eventDetailId)
                            .orElseThrow { NoSuchElementException("대회 정보 또는 GPX 파일 없음: eventDetailId=$eventDetailId") }
                        
                        val matcher = MapMatcher()
                        val waypoints = gpxRoute.waypoints
                        val result = matcher.matchToRoute(
                            gpsLat = filtered.first,
                            gpsLon = filtered.second,
                            bearing = gpsData.bearing?.toDouble() ?: 0.0,
                            waypoints = waypoints
                        )
                        
                        Pair(result.matchedLatitude, result.matchedLongitude)
                    } else {
                        // 코스 데이터가 있는 경우 보간된 포인트를 사용한 정밀 매칭
                        logger.debug("코스 데이터 발견: ${courseData.totalPoints}개 보간 포인트 사용")
                        
                        val matcher = MapMatcher()
                        val result = matcher.matchToInterpolatedRoute(
                            gpsLat = filtered.first,
                            gpsLon = filtered.second,
                            bearing = gpsData.bearing?.toDouble() ?: 0.0,
                            interpolatedPoints = courseData.interpolatedPoints
                        )
                        
                        Pair(result.matchedLatitude, result.matchedLongitude)
                    }
                    
                    finalCorrectedLat = correctedLat
                    finalCorrectedLng = correctedLng
                    
                    logger.info("최종 맵매칭 완료: 보정위치=(${"%.6f".format(correctedLat)}, ${"%.6f".format(correctedLng)})")
                    
                    // 실시간 위치 저장
                    try {
                        realtimeLocationService.saveParticipantLocation(
                            userId = userId,
                            eventDetailId = eventDetailId,
                            originalLat = lat,
                            originalLng = lng,
                            correctedLat = correctedLat,
                            correctedLng = correctedLng,
                            timestamp = timestamp
                        )
                    } catch (e: Exception) {
                        logger.warn("실시간 위치 저장 실패 (계속 진행): userId=$userId", e)
                    }
                }
            }
            
            logger.info("위치 보정 완료: ${gpsDataList.size}개 포인트 처리, 최종 보정위치=(${"%.6f".format(finalCorrectedLat)}, ${"%.6f".format(finalCorrectedLng)})")
            
            return CorrectLocationResponseDto(
                data = CorrectedLocationDataDto(
                    correctedLat = finalCorrectedLat,
                    correctedLng = finalCorrectedLng
                )
            )
            
        } catch (e: NoSuchElementException) {
            logger.error("위치 보정 실패 - 대회 데이터 없음: eventDetailId=${request.eventDetailId}", e)
            throw e
        } catch (e: IllegalArgumentException) {
            logger.error("위치 보정 실패 - 잘못된 파라미터: ${e.message}", e)
            throw e
        } catch (e: Exception) {
            logger.error("위치 보정 실패 - 예상치 못한 오류: userId=${request.userId}, eventDetailId=${request.eventDetailId}", e)
            throw RuntimeException("위치 보정 처리 중 오류가 발생했습니다: ${e.message}", e)
        }
    }
}

/**
 * 경로 통계 데이터 클래스
 * 
 * GPX 경로의 통계 정보를 담는 데이터 클래스입니다.
 * 
 * @property totalDistance 총 거리 (미터)
 * @property elevationGain 총 고도 상승 (미터)
 * @property elevationLoss 총 고도 하강 (미터)
 */
private data class RouteStatistics(
    val totalDistance: Double,
    val elevationGain: Double,
    val elevationLoss: Double
) 

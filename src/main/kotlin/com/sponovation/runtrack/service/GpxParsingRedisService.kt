package com.sponovation.runtrack.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.sponovation.runtrack.dto.*
import com.sponovation.runtrack.util.GeoUtils
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * GPX 파싱 데이터 Redis 저장 서비스
 * 
 * GPX 파일을 파싱하여 체크포인트와 보간 포인트를 식별하고
 * Redis에 저장하는 기능을 제공합니다.
 * 
 * 키 형태: gpx:{eventId}:{eventDetailId}
 */
@Service
class GpxParsingRedisService(
    private val redisTemplate: RedisTemplate<String, Any>,
    private val objectMapper: ObjectMapper,
    private val gpxParsingService: GpxParsingService,
    private val routeInterpolationService: RouteInterpolationService
) {
    
    private val logger = LoggerFactory.getLogger(GpxParsingRedisService::class.java)
    
    companion object {
        private const val GPX_KEY_PREFIX = "gpx:"
        private const val GPX_TTL_HOURS = 24L
        private const val DEFAULT_CHECKPOINT_DISTANCE = 1000.0 // 1km 간격
        private const val DEFAULT_INTERPOLATION_INTERVAL = 100.0 // 100m 간격
    }
    
    /**
     * GPX 파일을 파싱하여 Redis에 저장합니다.
     * 
     * @param file 업로드된 GPX 파일
     * @param eventId 이벤트 ID
     * @param eventDetailId 이벤트 상세 ID
     * @param checkpointDistanceInterval 체크포인트 간격 (미터, 기본 1km)
     * @param interpolationInterval 보간 간격 (미터, 기본 100m)
     * @return 저장 결과 DTO
     */
    fun parseAndSaveGpxFile(
        file: MultipartFile,
        eventId: Long,
        eventDetailId: Long,
        checkpointDistanceInterval: Double = DEFAULT_CHECKPOINT_DISTANCE,
        interpolationInterval: Double = DEFAULT_INTERPOLATION_INTERVAL
    ): GpxParsingResponseDto {
        logger.info("GPX 파일 파싱 및 Redis 저장 시작: eventId=$eventId, eventDetailId=$eventDetailId, file=${file.originalFilename}")
        
        try {
            // 파일 검증
            validateFile(file)
            
            // GPX 파일 파싱
            val waypoints = gpxParsingService.parseGpxFile(file.bytes)
            logger.info("GPX 파일 파싱 완료: ${waypoints.size}개 웨이포인트")
            
            // 보간 포인트 생성
            val interpolatedPoints = routeInterpolationService.interpolateRoute(waypoints)
            logger.info("경로 보간 완료: ${interpolatedPoints.size}개 보간 포인트")
            
            // GPX 파싱 포인트 생성 (체크포인트 식별 포함)
            val gpxPoints = createGpxParsingPoints(
                interpolatedPoints,
                checkpointDistanceInterval,
                interpolationInterval
            )
            
            // Redis에 저장
            val redisKey = generateRedisKey(eventId, eventDetailId)
            saveToRedis(redisKey, gpxPoints)
            
            // 통계 계산
            val totalDistance = calculateTotalDistance(gpxPoints)
            val checkpointCount = gpxPoints.count { it.type == "checkpoint" || it.type == "start" || it.type == "finish" }
            val interpolatedPointCount = gpxPoints.count { it.type == "interpolated" }
            
            logger.info("GPX 파싱 데이터 Redis 저장 완료: key=$redisKey, 총 ${gpxPoints.size}개 포인트")
            
            return GpxParsingResponseDto(
                success = true,
                message = "GPX 파일 파싱 및 저장이 완료되었습니다.",
                eventId = eventId,
                eventDetailId = eventDetailId,
                totalPoints = gpxPoints.size,
                checkpointCount = checkpointCount,
                interpolatedPointCount = interpolatedPointCount,
                totalDistance = totalDistance,
                redisKey = redisKey,
                createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            )
            
        } catch (e: Exception) {
            logger.error("GPX 파싱 및 Redis 저장 실패: eventId=$eventId, eventDetailId=$eventDetailId", e)
            return GpxParsingResponseDto(
                success = false,
                message = "GPX 파일 처리 중 오류가 발생했습니다: ${e.message}",
                eventId = eventId,
                eventDetailId = eventDetailId,
                totalPoints = 0,
                checkpointCount = 0,
                interpolatedPointCount = 0,
                totalDistance = 0.0,
                redisKey = "",
                createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            )
        }
    }
    
    /**
     * Redis에서 GPX 파싱 데이터를 조회합니다.
     * 
     * @param eventId 이벤트 ID
     * @param eventDetailId 이벤트 상세 ID
     * @return 조회 결과 DTO
     */
    fun getGpxParsingData(eventId: Long, eventDetailId: Long): GpxParsingDataResponseDto {
        logger.info("GPX 파싱 데이터 조회: eventId=$eventId, eventDetailId=$eventDetailId")
        
        return try {
            val redisKey = generateRedisKey(eventId, eventDetailId)
            val jsonData = redisTemplate.opsForValue().get(redisKey) as? String
            
            if (jsonData != null) {
                val points = objectMapper.readValue(jsonData, Array<GpxParsingPointDto>::class.java).toList()
                logger.info("GPX 파싱 데이터 조회 성공: ${points.size}개 포인트")
                
                GpxParsingDataResponseDto(
                    success = true,
                    message = "GPX 파싱 데이터 조회가 완료되었습니다.",
                    eventId = eventId,
                    eventDetailId = eventDetailId,
                    points = points
                )
            } else {
                logger.warn("GPX 파싱 데이터 없음: key=$redisKey")
                GpxParsingDataResponseDto(
                    success = false,
                    message = "해당 이벤트의 GPX 파싱 데이터가 없습니다.",
                    eventId = eventId,
                    eventDetailId = eventDetailId,
                    points = emptyList()
                )
            }
            
        } catch (e: Exception) {
            logger.error("GPX 파싱 데이터 조회 실패: eventId=$eventId, eventDetailId=$eventDetailId", e)
            GpxParsingDataResponseDto(
                success = false,
                message = "GPX 파싱 데이터 조회 중 오류가 발생했습니다: ${e.message}",
                eventId = eventId,
                eventDetailId = eventDetailId,
                points = emptyList()
            )
        }
    }
    
    /**
     * 체크포인트 식별 및 GPX 파싱 포인트 생성
     */
    private fun createGpxParsingPoints(
        interpolatedPoints: List<InterpolatedPoint>,
        checkpointDistanceInterval: Double,
        interpolationInterval: Double
    ): List<GpxParsingPointDto> {
        val gpxPoints = mutableListOf<GpxParsingPointDto>()
        var cpIndex = 0
        
        interpolatedPoints.forEachIndexed { index, point ->
            val (type, cpId, cpIndexValue) = determinePointType(
                point, 
                index, 
                interpolatedPoints, 
                checkpointDistanceInterval,
                cpIndex
            )
            
            // 체크포인트인 경우 cpIndex 증가
            if (type == "checkpoint" || type == "start" || type == "finish") {
                cpIndex++
            }
            
            gpxPoints.add(
                GpxParsingPointDto(
                    latitude = point.latitude,
                    longitude = point.longitude,
                    altitude = point.elevation,
                    sequence = index + 1,
                    type = type,
                    cpId = cpId,
                    cpIndex = cpIndexValue
                )
            )
        }
        
        return gpxPoints
    }
    
    /**
     * 포인트 타입 결정 (start, interpolated, checkpoint, finish)
     */
    private fun determinePointType(
        point: InterpolatedPoint,
        index: Int,
        allPoints: List<InterpolatedPoint>,
        checkpointDistanceInterval: Double,
        currentCpIndex: Int
    ): Triple<String, String?, Int?> {
        return when {
            // 시작점
            index == 0 -> Triple("start", "START", 0)
            
            // 종료점
            index == allPoints.size - 1 -> Triple("finish", "FINISH", currentCpIndex)
            
            // 체크포인트 (거리 기준)
            point.distanceFromStart > 0 && 
            point.distanceFromStart % checkpointDistanceInterval < 100 -> {
                val cpNumber = (point.distanceFromStart / checkpointDistanceInterval).toInt()
                Triple("checkpoint", "CP$cpNumber", currentCpIndex)
            }
            
            // 보간 포인트
            else -> Triple("interpolated", null, null)
        }
    }
    
    /**
     * Redis 키 생성
     */
    private fun generateRedisKey(eventId: Long, eventDetailId: Long): String {
        return "$GPX_KEY_PREFIX$eventId:$eventDetailId"
    }
    
    /**
     * Redis에 데이터 저장
     */
    private fun saveToRedis(key: String, points: List<GpxParsingPointDto>) {
        try {
            val jsonData = objectMapper.writeValueAsString(points)
            redisTemplate.opsForValue().set(key, jsonData, GPX_TTL_HOURS, TimeUnit.HOURS)
            logger.info("Redis 저장 완료: key=$key, 데이터 크기=${jsonData.length} bytes")
        } catch (e: Exception) {
            logger.error("Redis 저장 실패: key=$key", e)
            throw RuntimeException("Redis 저장 중 오류가 발생했습니다: ${e.message}", e)
        }
    }
    
    /**
     * 파일 유효성 검증
     */
    private fun validateFile(file: MultipartFile) {
        if (file.isEmpty) {
            throw IllegalArgumentException("업로드된 파일이 비어있습니다.")
        }
        
        if (file.size > 10 * 1024 * 1024) { // 10MB 제한
            throw IllegalArgumentException("파일 크기가 너무 큽니다. (최대 10MB)")
        }
        
        val filename = file.originalFilename ?: ""
        if (!filename.lowercase().endsWith(".gpx")) {
            throw IllegalArgumentException("GPX 파일만 업로드할 수 있습니다.")
        }
    }
    
    /**
     * 총 거리 계산
     */
    private fun calculateTotalDistance(points: List<GpxParsingPointDto>): Double {
        if (points.size < 2) return 0.0
        
        var totalDistance = 0.0
        for (i in 1 until points.size) {
            val prevPoint = points[i - 1]
            val currentPoint = points[i]
            val distance = GeoUtils.calculateDistance(
                prevPoint.latitude, prevPoint.longitude,
                currentPoint.latitude, currentPoint.longitude
            )
            totalDistance += distance
        }
        return totalDistance
    }
    
    /**
     * Redis에서 GPX 파싱 데이터 삭제
     */
    fun deleteGpxParsingData(eventId: Long, eventDetailId: Long): Boolean {
        return try {
            val redisKey = generateRedisKey(eventId, eventDetailId)
            val deleted = redisTemplate.delete(redisKey)
            logger.info("GPX 파싱 데이터 삭제: key=$redisKey, 성공=$deleted")
            deleted
        } catch (e: Exception) {
            logger.error("GPX 파싱 데이터 삭제 실패: eventId=$eventId, eventDetailId=$eventDetailId", e)
            false
        }
    }
    
    /**
     * 패턴으로 GPX 파싱 데이터 키 조회
     */
    fun getAllGpxParsingKeys(eventId: Long? = null): List<String> {
        return try {
            val pattern = if (eventId != null) {
                "$GPX_KEY_PREFIX$eventId:*"
            } else {
                "$GPX_KEY_PREFIX*"
            }
            
            val keys = redisTemplate.keys(pattern)?.toList() ?: emptyList()
            logger.info("GPX 파싱 키 조회: 패턴=$pattern, 발견된 키=${keys.size}개")
            keys
        } catch (e: Exception) {
            logger.error("GPX 파싱 키 조회 실패: eventId=$eventId", e)
            emptyList()
        }
    }
} 
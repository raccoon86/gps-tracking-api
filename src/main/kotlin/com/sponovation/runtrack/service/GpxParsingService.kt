package com.sponovation.runtrack.service

import io.jenetics.jpx.GPX
import io.jenetics.jpx.Track
import io.jenetics.jpx.Route
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

@Service
class GpxParsingService {

    private val logger = LoggerFactory.getLogger(GpxParsingService::class.java)

    /**
     * GPX 바이트 배열을 파싱하여 웨이포인트 목록을 반환합니다.
     */
    fun parseGpxFile(fileBytes: ByteArray): List<ParsedGpxWaypoint> {
        logger.debug("GPX 바이트 배열 파싱 시작: ${fileBytes.size}bytes")
        
        // 파일 크기 검증
        if (fileBytes.isEmpty()) {
            logger.error("GPX 바이트 배열이 비어있습니다")
            throw GpxParsingException("업로드된 파일이 비어있습니다")
        }
        
        // 파일 헤더 검증 (간단한 GPX 형식 확인)
        val headerString = String(fileBytes.take(100).toByteArray())
        if (!headerString.contains("<?xml") && !headerString.contains("<gpx")) {
            logger.error("유효하지 않은 GPX 파일 형식: 헤더=${headerString.take(50)}")
            throw GpxParsingException("유효하지 않은 GPX 파일 형식입니다. XML 헤더가 없습니다.")
        }
        
        return parseGpxFile(java.io.ByteArrayInputStream(fileBytes))
    }
    
    /**
     * GPX 파일을 파싱하여 웨이포인트 목록을 반환합니다.
     */
    fun parseGpxFile(inputStream: InputStream): List<ParsedGpxWaypoint> {
        var tempFile: Path? = null
        return try {
            logger.debug("GPX InputStream 파싱 시작")
            
            // InputStream을 임시 파일로 저장 (리소스 안전하게 관리)
            tempFile = try {
                Files.createTempFile("gpx_", ".gpx")
            } catch (e: Exception) {
                logger.error("임시 파일 생성 실패: ${e.message}", e)
                throw GpxParsingException("임시 파일 생성에 실패했습니다. 서버 디스크 공간을 확인해주세요.", e)
            }
            
            logger.debug("임시 GPX 파일 생성: ${tempFile}")
            
            // 파일 복사 및 크기 검증
            val copiedBytes = try {
                inputStream.use { input ->
                    Files.copy(input, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                }
            } catch (e: Exception) {
                logger.error("InputStream을 임시 파일로 복사 실패: ${e.message}", e)
                throw GpxParsingException("파일 복사 중 오류가 발생했습니다. 파일이 손상되었을 수 있습니다.", e)
            }
            
            logger.debug("파일 복사 완료: ${copiedBytes}bytes")
            
            // 복사된 파일 크기 검증
            val fileSize = Files.size(tempFile)
            if (fileSize == 0L) {
                logger.error("복사된 GPX 파일이 비어있습니다")
                throw GpxParsingException("업로드된 파일이 비어있거나 손상되었습니다")
            }
            
            if (fileSize > 10 * 1024 * 1024) { // 10MB 제한
                logger.error("GPX 파일 크기가 너무 큽니다: ${fileSize}bytes")
                throw GpxParsingException("파일 크기가 너무 큽니다 (최대 10MB)")
            }
            
            // 파일 내용 미리보기 (디버깅용)
            try {
                val fileContent = Files.readString(tempFile).take(200)
                logger.debug("GPX 파일 내용 미리보기: ${fileContent}")
                
                // 기본적인 GPX 형식 검증
                if (!fileContent.contains("<?xml") && !fileContent.contains("<gpx")) {
                    logger.error("유효하지 않은 GPX 파일 형식")
                    throw GpxParsingException("유효하지 않은 GPX 파일 형식입니다. XML 또는 GPX 태그를 찾을 수 없습니다.")
                }
            } catch (e: GpxParsingException) {
                throw e // 다시 던지기
            } catch (e: Exception) {
                logger.warn("파일 내용 미리보기 실패 (계속 진행): ${e.message}")
            }
            
            // GPX 파싱 시도
            val gpx = try {
                logger.debug("GPX 라이브러리로 파싱 시작")
                GPX.read(tempFile)
            } catch (e: Exception) {
                logger.error("GPX 파일 파싱 실패: ${e.javaClass.simpleName} - ${e.message}", e)
                
                // 구체적인 에러 메시지 제공
                val errorMessage = when {
                    e.message?.contains("XML") == true || e.message?.contains("parse") == true -> 
                        "GPX 파일의 XML 형식이 올바르지 않습니다. 파일이 손상되었거나 잘못된 형식일 수 있습니다."
                    e.message?.contains("schema") == true || e.message?.contains("namespace") == true -> 
                        "GPX 파일의 스키마가 지원되지 않는 형식입니다. 표준 GPX 1.1 형식을 사용해주세요."
                    e.message?.contains("encoding") == true -> 
                        "GPX 파일의 문자 인코딩에 문제가 있습니다. UTF-8 인코딩을 사용해주세요."
                    else -> "GPX 파일 파싱에 실패했습니다. 올바른 GPX 형식인지 확인해주세요."
                }
                
                throw GpxParsingException(errorMessage, e)
            }
            
            logger.debug("GPX 파일 파싱 성공")
            
            val waypoints = mutableListOf<ParsedGpxWaypoint>()

            // Track의 TrackSegment에서 웨이포인트 추출
            var trackCount = 0
            gpx.tracks().forEach { track: Track ->
                trackCount++
                logger.debug("Track ${trackCount} 처리 중...")
                
                var segmentCount = 0
                track.segments().forEach { segment ->
                    segmentCount++
                    logger.debug("Segment ${segmentCount} 처리 중: ${segment.points().count()}개 포인트")
                    
                    segment.points().forEach { point ->
                        waypoints.add(
                            ParsedGpxWaypoint(
                                latitude = point.latitude.toDegrees(),
                                longitude = point.longitude.toDegrees(),
                                elevation = if (point.elevation.isPresent) point.elevation.get().toDouble() else 0.0,
                                timestamp = if (point.time.isPresent) point.time.get() else Instant.now()
                            )
                        )
                    }
                }
            }

            // Route에서 웨이포인트 추출 (Track이 없는 경우)
            if (waypoints.isEmpty()) {
                logger.debug("Track이 없어서 Route에서 웨이포인트 추출 시도")
                
                var routeCount = 0
                gpx.routes().forEach { route: Route ->
                    routeCount++
                    logger.debug("Route ${routeCount} 처리 중: ${route.points().count()}개 포인트")
                    
                    route.points().forEach { point ->
                        waypoints.add(
                            ParsedGpxWaypoint(
                                latitude = point.latitude.toDegrees(),
                                longitude = point.longitude.toDegrees(),
                                elevation = if (point.elevation.isPresent) point.elevation.get().toDouble() else 0.0,
                                timestamp = if (point.time.isPresent) point.time.get() else Instant.now()
                            )
                        )
                    }
                }
            }
            
            // 웨이포인트 개수 검증
            if (waypoints.isEmpty()) {
                logger.error("GPX 파일에서 웨이포인트를 찾을 수 없습니다")
                throw GpxParsingException("GPX 파일에 경로 데이터(Track 또는 Route)가 없습니다. 올바른 GPX 파일인지 확인해주세요.")
            }
            
            if (waypoints.size < 2) {
                logger.error("웨이포인트가 너무 적습니다: ${waypoints.size}개")
                throw GpxParsingException("경로를 생성하기 위해서는 최소 2개 이상의 좌표점이 필요합니다. (현재: ${waypoints.size}개)")
            }
            
            logger.info("GPX 파일 파싱 완료: ${waypoints.size}개 포인트")
            logger.debug("첫 번째 포인트: (${waypoints.first().latitude}, ${waypoints.first().longitude})")
            logger.debug("마지막 포인트: (${waypoints.last().latitude}, ${waypoints.last().longitude})")
            
            waypoints
            
        } catch (e: GpxParsingException) {
            // 이미 처리된 예외는 그대로 전파
            throw e
        } catch (e: Exception) {
            logger.error("GPX 파일 파싱 중 예상치 못한 오류: ${e.javaClass.simpleName} - ${e.message}", e)
            throw GpxParsingException("GPX 파일 처리 중 예상치 못한 오류가 발생했습니다: ${e.message}", e)
        } finally {
            // 임시 파일 정리
            tempFile?.let { file ->
                try {
                    if (Files.exists(file)) {
                        Files.delete(file)
                        logger.debug("임시 GPX 파일 삭제: ${file}")
                    }
                } catch (e: Exception) {
                    logger.warn("임시 파일 삭제 실패: ${file} - ${e.message}")
                }
            }
        }
    }
}

/**
 * GPX 웨이포인트 데이터 클래스 (파싱용)
 */
data class ParsedGpxWaypoint(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double,
    val timestamp: Instant
)

/**
 * GPX 파싱 관련 예외
 */
class GpxParsingException(message: String, cause: Throwable? = null) : RuntimeException(message, cause) 
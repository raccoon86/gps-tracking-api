package com.sponovation.runtrack.repository

import com.sponovation.runtrack.domain.ParticipantLocation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.util.*

@Repository
interface ParticipantLocationRepository : JpaRepository<ParticipantLocation, Long> {

    // === 시간 기반 조회 ===

    /**
     * 특정 시간 범위 내의 위치 데이터 조회
     */
    @Query("SELECT pl FROM ParticipantLocation pl WHERE pl.rawTime BETWEEN :startTime AND :endTime ORDER BY pl.rawTime ASC")
    fun findByRawTimeBetween(
        @Param("startTime") startTime: Long,
        @Param("endTime") endTime: Long
    ): List<ParticipantLocation>

    /**
     * 특정 시간 이후의 위치 데이터 조회
     */
    @Query("SELECT pl FROM ParticipantLocation pl WHERE pl.rawTime >= :startTime ORDER BY pl.rawTime ASC")
    fun findByRawTimeAfter(@Param("startTime") startTime: Long): List<ParticipantLocation>

    /**
     * 최신 업데이트 시간 기준으로 조회
     */
    @Query("SELECT pl FROM ParticipantLocation pl WHERE pl.lastUpdated >= :timestamp ORDER BY pl.lastUpdated DESC")
    fun findByLastUpdatedAfter(@Param("timestamp") timestamp: Long): List<ParticipantLocation>

    /**
     * 가장 최근 위치 데이터 조회
     */
    @Query("SELECT pl FROM ParticipantLocation pl ORDER BY pl.lastUpdated DESC LIMIT 1")
    fun findLatestLocation(): ParticipantLocation?

    /**
     * 특정 시간 간격 내의 위치 데이터 개수 조회
     */
    @Query("SELECT COUNT(pl) FROM ParticipantLocation pl WHERE pl.rawTime BETWEEN :startTime AND :endTime")
    fun countByRawTimeBetween(
        @Param("startTime") startTime: Long,
        @Param("endTime") endTime: Long
    ): Long

    // === 위치 기반 조회 ===

    /**
     * 특정 영역 내의 위치 데이터 조회
     */
    @Query("""
        SELECT pl FROM ParticipantLocation pl 
        WHERE (pl.correctedLatitude IS NOT NULL AND pl.correctedLongitude IS NOT NULL 
               AND pl.correctedLatitude BETWEEN :minLat AND :maxLat 
               AND pl.correctedLongitude BETWEEN :minLng AND :maxLng)
           OR (pl.correctedLatitude IS NULL AND pl.correctedLongitude IS NULL 
               AND pl.rawLatitude BETWEEN :minLat AND :maxLat 
               AND pl.rawLongitude BETWEEN :minLng AND :maxLng)
        ORDER BY pl.lastUpdated DESC
    """)
    fun findByLocationBounds(
        @Param("minLat") minLatitude: BigDecimal,
        @Param("maxLat") maxLatitude: BigDecimal,
        @Param("minLng") minLongitude: BigDecimal,
        @Param("maxLng") maxLongitude: BigDecimal
    ): List<ParticipantLocation>

    /**
     * 특정 반경 내의 위치 데이터 조회 (Haversine 공식 사용)
     */
    @Query("""
        SELECT pl FROM ParticipantLocation pl
        WHERE (6371 * acos(cos(radians(:centerLat)) 
                      * cos(radians(COALESCE(pl.correctedLatitude, pl.rawLatitude))) 
                      * cos(radians(COALESCE(pl.correctedLongitude, pl.rawLongitude)) - radians(:centerLng)) 
                      + sin(radians(:centerLat)) 
                      * sin(radians(COALESCE(pl.correctedLatitude, pl.rawLatitude))))) <= :radiusKm
        ORDER BY pl.lastUpdated DESC
    """)
    fun findWithinRadius(
        @Param("centerLat") centerLatitude: BigDecimal,
        @Param("centerLng") centerLongitude: BigDecimal,
        @Param("radiusKm") radiusKm: BigDecimal
    ): List<ParticipantLocation>

    /**
     * 보정된 위치 데이터만 조회
     */
    @Query("SELECT pl FROM ParticipantLocation pl WHERE pl.correctedLatitude IS NOT NULL AND pl.correctedLongitude IS NOT NULL ORDER BY pl.lastUpdated DESC")
    fun findCorrectedLocations(): List<ParticipantLocation>

    /**
     * 원본 위치 데이터만 조회 (보정되지 않은 것)
     */
    @Query("SELECT pl FROM ParticipantLocation pl WHERE pl.correctedLatitude IS NULL OR pl.correctedLongitude IS NULL ORDER BY pl.lastUpdated DESC")
    fun findUncorrectedLocations(): List<ParticipantLocation>

    // === 체크포인트 관련 조회 ===

    /**
     * 특정 체크포인트에 도달한 위치 데이터 조회
     */
    @Query("SELECT pl FROM ParticipantLocation pl WHERE pl.farthestCpId = :cpId ORDER BY pl.lastUpdated DESC")
    fun findByFarthestCpId(@Param("cpId") cpId: String): List<ParticipantLocation>

    /**
     * 특정 체크포인트 인덱스에 도달한 위치 데이터 조회
     */
    @Query("SELECT pl FROM ParticipantLocation pl WHERE pl.farthestCpIndex = :cpIndex ORDER BY pl.lastUpdated DESC")
    fun findByFarthestCpIndex(@Param("cpIndex") cpIndex: Int): List<ParticipantLocation>

    /**
     * 특정 체크포인트 인덱스 이상에 도달한 위치 데이터 조회
     */
    @Query("SELECT pl FROM ParticipantLocation pl WHERE pl.farthestCpIndex >= :minCpIndex ORDER BY pl.farthestCpIndex ASC, pl.lastUpdated DESC")
    fun findByFarthestCpIndexGreaterThanEqual(@Param("minCpIndex") minCpIndex: Int): List<ParticipantLocation>

    /**
     * 체크포인트에 도달한 위치 데이터 조회
     */
    @Query("SELECT pl FROM ParticipantLocation pl WHERE pl.farthestCpId IS NOT NULL AND pl.farthestCpIndex IS NOT NULL ORDER BY pl.farthestCpIndex ASC, pl.lastUpdated DESC")
    fun findWithCheckpointReached(): List<ParticipantLocation>

    /**
     * 체크포인트별 도달 통계
     */
    @Query("SELECT pl.farthestCpId, pl.farthestCpIndex, COUNT(pl) as count FROM ParticipantLocation pl WHERE pl.farthestCpId IS NOT NULL GROUP BY pl.farthestCpId, pl.farthestCpIndex ORDER BY pl.farthestCpIndex ASC")
    fun getCheckpointReachStatistics(): List<Array<Any>>

    // === 거리 및 속도 관련 조회 ===

    /**
     * 특정 거리 이상 이동한 위치 데이터 조회
     */
    @Query("SELECT pl FROM ParticipantLocation pl WHERE pl.distanceCovered >= :minDistance ORDER BY pl.distanceCovered DESC")
    fun findByDistanceCoveredGreaterThanEqual(@Param("minDistance") minDistance: BigDecimal): List<ParticipantLocation>

    /**
     * 특정 거리 범위 내의 위치 데이터 조회
     */
    @Query("SELECT pl FROM ParticipantLocation pl WHERE pl.distanceCovered BETWEEN :minDistance AND :maxDistance ORDER BY pl.distanceCovered ASC")
    fun findByDistanceCoveredBetween(
        @Param("minDistance") minDistance: BigDecimal,
        @Param("maxDistance") maxDistance: BigDecimal
    ): List<ParticipantLocation>

    /**
     * 특정 속도 이상의 위치 데이터 조회
     */
    @Query("SELECT pl FROM ParticipantLocation pl WHERE pl.rawSpeed >= :minSpeed ORDER BY pl.rawSpeed DESC")
    fun findByRawSpeedGreaterThanEqual(@Param("minSpeed") minSpeed: BigDecimal): List<ParticipantLocation>

    /**
     * 최대 이동 거리 조회
     */
    @Query("SELECT MAX(pl.distanceCovered) FROM ParticipantLocation pl")
    fun findMaxDistanceCovered(): BigDecimal?

    /**
     * 최대 속도 조회
     */
    @Query("SELECT MAX(pl.rawSpeed) FROM ParticipantLocation pl")
    fun findMaxRawSpeed(): BigDecimal?

    /**
     * 평균 이동 거리 조회
     */
    @Query("SELECT AVG(pl.distanceCovered) FROM ParticipantLocation pl")
    fun findAverageDistanceCovered(): BigDecimal?

    /**
     * 평균 속도 조회
     */
    @Query("SELECT AVG(pl.rawSpeed) FROM ParticipantLocation pl WHERE pl.rawSpeed IS NOT NULL")
    fun findAverageRawSpeed(): BigDecimal?

    // === 정확도 관련 조회 ===

    /**
     * 특정 정확도 이하의 위치 데이터 조회
     */
    @Query("SELECT pl FROM ParticipantLocation pl WHERE pl.rawAccuracy <= :maxAccuracy ORDER BY pl.rawAccuracy ASC")
    fun findByRawAccuracyLessThanEqual(@Param("maxAccuracy") maxAccuracy: BigDecimal): List<ParticipantLocation>

    /**
     * 정확도가 양호한 위치 데이터 조회
     */
    @Query("SELECT pl FROM ParticipantLocation pl WHERE pl.rawAccuracy IS NOT NULL AND pl.rawAccuracy <= :thresholdMeters ORDER BY pl.lastUpdated DESC")
    fun findGoodAccuracyLocations(@Param("thresholdMeters") thresholdMeters: BigDecimal): List<ParticipantLocation>

    /**
     * 정확도가 불량한 위치 데이터 조회
     */
    @Query("SELECT pl FROM ParticipantLocation pl WHERE pl.rawAccuracy IS NULL OR pl.rawAccuracy > :thresholdMeters ORDER BY pl.lastUpdated DESC")
    fun findPoorAccuracyLocations(@Param("thresholdMeters") thresholdMeters: BigDecimal): List<ParticipantLocation>

    // === 시간 관련 조회 ===

    /**
     * 특정 누적 시간 이상의 위치 데이터 조회
     */
    @Query("SELECT pl FROM ParticipantLocation pl WHERE pl.cumulativeTime >= :minTime ORDER BY pl.cumulativeTime ASC")
    fun findByCumulativeTimeGreaterThanEqual(@Param("minTime") minTime: Int): List<ParticipantLocation>

    /**
     * 특정 누적 시간 범위 내의 위치 데이터 조회
     */
    @Query("SELECT pl FROM ParticipantLocation pl WHERE pl.cumulativeTime BETWEEN :minTime AND :maxTime ORDER BY pl.cumulativeTime ASC")
    fun findByCumulativeTimeBetween(
        @Param("minTime") minTime: Int,
        @Param("maxTime") maxTime: Int
    ): List<ParticipantLocation>

    /**
     * 최대 누적 시간 조회
     */
    @Query("SELECT MAX(pl.cumulativeTime) FROM ParticipantLocation pl")
    fun findMaxCumulativeTime(): Int?

    /**
     * 평균 누적 시간 조회
     */
    @Query("SELECT AVG(pl.cumulativeTime) FROM ParticipantLocation pl")
    fun findAverageCumulativeTime(): Double?

    // === 진행 방향 관련 조회 ===

    /**
     * 특정 진행 방향 범위의 위치 데이터 조회
     */
    @Query("SELECT pl FROM ParticipantLocation pl WHERE pl.heading BETWEEN :minHeading AND :maxHeading ORDER BY pl.lastUpdated DESC")
    fun findByHeadingBetween(
        @Param("minHeading") minHeading: BigDecimal,
        @Param("maxHeading") maxHeading: BigDecimal
    ): List<ParticipantLocation>

    /**
     * 진행 방향이 설정된 위치 데이터 조회
     */
    @Query("SELECT pl FROM ParticipantLocation pl WHERE pl.heading IS NOT NULL ORDER BY pl.lastUpdated DESC")
    fun findWithHeading(): List<ParticipantLocation>

    // === 데이터 정리 및 유지보수 ===

    /**
     * 특정 시간 이전의 데이터 삭제
     */
    @Query("DELETE FROM ParticipantLocation pl WHERE pl.rawTime < :cutoffTime")
    fun deleteOldData(@Param("cutoffTime") cutoffTime: Long): Int

    /**
     * 정확도가 불량한 데이터 삭제
     */
    @Query("DELETE FROM ParticipantLocation pl WHERE pl.rawAccuracy IS NOT NULL AND pl.rawAccuracy > :maxAccuracy")
    fun deletePoorAccuracyData(@Param("maxAccuracy") maxAccuracy: BigDecimal): Int

    /**
     * 전체 데이터 개수 조회
     */
    @Query("SELECT COUNT(pl) FROM ParticipantLocation pl")
    fun getTotalLocationCount(): Long

    /**
     * 보정된 데이터 개수 조회
     */
    @Query("SELECT COUNT(pl) FROM ParticipantLocation pl WHERE pl.correctedLatitude IS NOT NULL AND pl.correctedLongitude IS NOT NULL")
    fun getCorrectedLocationCount(): Long

    /**
     * 체크포인트 도달 데이터 개수 조회
     */
    @Query("SELECT COUNT(pl) FROM ParticipantLocation pl WHERE pl.farthestCpId IS NOT NULL")
    fun getCheckpointReachedCount(): Long
} 
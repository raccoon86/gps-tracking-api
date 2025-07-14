package com.sponovation.runtrack.domain

import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(
    name = "participant_locations",
    indexes = [
        Index(name = "idx_participant_location_last_updated", columnList = "lastUpdated"),
        Index(name = "idx_participant_location_raw_time", columnList = "rawTime"),
        Index(name = "idx_participant_location_farthest_cp", columnList = "farthestCpId"),
        Index(name = "idx_participant_location_coords", columnList = "correctedLatitude,correctedLongitude"),
        Index(name = "idx_participant_location_distance", columnList = "distanceCovered")
    ]
)
data class ParticipantLocation(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0L,

    @Column(name = "raw_latitude", precision = 15, scale = 10, nullable = false)
    val rawLatitude: BigDecimal,

    @Column(name = "raw_longitude", precision = 15, scale = 10, nullable = false)
    val rawLongitude: BigDecimal,

    @Column(name = "raw_altitude", precision = 10, scale = 3, nullable = true)
    val rawAltitude: BigDecimal? = null,

    @Column(name = "raw_accuracy", precision = 10, scale = 3, nullable = true)
    val rawAccuracy: BigDecimal? = null,

    @Column(name = "raw_time", nullable = false)
    val rawTime: Long,

    @Column(name = "raw_speed", precision = 10, scale = 3, nullable = true)
    val rawSpeed: BigDecimal? = null,

    @Column(name = "corrected_latitude", precision = 15, scale = 10, nullable = true)
    val correctedLatitude: BigDecimal? = null,

    @Column(name = "corrected_longitude", precision = 15, scale = 10, nullable = true)
    val correctedLongitude: BigDecimal? = null,

    @Column(name = "corrected_altitude", precision = 10, scale = 3, nullable = true)
    val correctedAltitude: BigDecimal? = null,

    @Column(name = "last_updated", nullable = false)
    val lastUpdated: Long,

    @Column(name = "heading", precision = 6, scale = 2, nullable = true)
    val heading: BigDecimal? = null,

    @Column(name = "distance_covered", precision = 12, scale = 3, nullable = false)
    val distanceCovered: BigDecimal = BigDecimal.ZERO,

    @Column(name = "cumulative_time", nullable = false)
    val cumulativeTime: Int = 0,

    @Column(name = "farthest_cp_id", length = 50, nullable = true)
    val farthestCpId: String? = null,

    @Column(name = "farthest_cp_index", nullable = true)
    val farthestCpIndex: Int? = null,

    @Column(name = "cumulative_time_at_farthest_cp", nullable = true)
    val cumulativeTimeAtFarthestCp: Int? = null
) {
    
    /**
     * 원본 GPS 좌표를 반환
     */
    fun getRawCoordinates(): Pair<BigDecimal, BigDecimal> {
        return Pair(rawLatitude, rawLongitude)
    }

    /**
     * 보정된 GPS 좌표를 반환 (보정되지 않은 경우 원본 좌표 반환)
     */
    fun getCorrectedCoordinates(): Pair<BigDecimal, BigDecimal> {
        return Pair(
            correctedLatitude ?: rawLatitude,
            correctedLongitude ?: rawLongitude
        )
    }

    /**
     * 원본 GPS 데이터가 보정되었는지 여부
     */
    fun isCorrected(): Boolean {
        return correctedLatitude != null && correctedLongitude != null
    }

    /**
     * 평균 속도 계산 (m/s)
     */
    fun getAverageSpeed(): BigDecimal {
        return if (cumulativeTime > 0) {
            distanceCovered.divide(BigDecimal.valueOf(cumulativeTime.toLong()), 3, java.math.RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }
    }

    /**
     * 평균 페이스 계산 (초/km)
     */
    fun getAveragePace(): BigDecimal {
        val distanceInKm = distanceCovered.divide(BigDecimal.valueOf(1000), 3, java.math.RoundingMode.HALF_UP)
        return if (distanceInKm > BigDecimal.ZERO) {
            BigDecimal.valueOf(cumulativeTime.toLong()).divide(distanceInKm, 0, java.math.RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }
    }

    /**
     * 시간 형식 변환 (HH:MM:SS)
     */
    fun getFormattedCumulativeTime(): String {
        val hours = cumulativeTime / 3600
        val minutes = (cumulativeTime % 3600) / 60
        val seconds = cumulativeTime % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    /**
     * 페이스 형식 변환 (MM:SS per km)
     */
    fun getFormattedPace(): String {
        val paceInSeconds = getAveragePace().toInt()
        val minutes = paceInSeconds / 60
        val seconds = paceInSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    /**
     * GPS 정확도가 양호한지 확인
     */
    fun hasGoodAccuracy(thresholdMeters: Double = 10.0): Boolean {
        return rawAccuracy?.let { it.toDouble() <= thresholdMeters } ?: false
    }

    /**
     * 체크포인트에 도달했는지 여부
     */
    fun hasReachedCheckpoint(): Boolean {
        return farthestCpId != null && farthestCpIndex != null
    }

    /**
     * 위치 정보의 유효성 검증
     */
    fun isValidLocation(): Boolean {
        return rawLatitude.toDouble() in -90.0..90.0 && 
               rawLongitude.toDouble() in -180.0..180.0 &&
               rawTime > 0 &&
               lastUpdated > 0
    }
} 
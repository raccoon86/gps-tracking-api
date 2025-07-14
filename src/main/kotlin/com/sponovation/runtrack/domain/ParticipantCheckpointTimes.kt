package com.sponovation.runtrack.domain

import jakarta.persistence.*
import java.util.*

@Entity
@Table(
    name = "participant_checkpoint_times",
    indexes = [
        Index(name = "idx_participant_checkpoint_times_participant", columnList = "participantId"),
        Index(name = "idx_participant_checkpoint_times_checkpoint", columnList = "checkpointId"),
        Index(name = "idx_participant_checkpoint_times_pass_time", columnList = "passTime"),
        Index(name = "idx_participant_checkpoint_times_cumulative", columnList = "cumulativeTime"),
        Index(name = "idx_participant_checkpoint_times_participant_checkpoint", columnList = "participantId,checkpointId")
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_participant_checkpoint", columnNames = ["participantId", "checkpointId"])
    ]
)
data class ParticipantCheckpointTimes(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0L,

    @Column(name = "participant_id", nullable = false)
    val participantId: Long,

    @Column(name = "checkpoint_id", length = 50, nullable = false)
    val checkpointId: String,

    @Column(name = "pass_time", nullable = false)
    val passTime: Long,

    @Column(name = "segment_duration", nullable = false)
    val segmentDuration: Int,

    @Column(name = "cumulative_time", nullable = false)
    val cumulativeTime: Int,

    @Column(name = "created_at", nullable = false)
    val createdAt: Long = System.currentTimeMillis(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Long = System.currentTimeMillis()
) {
    
    /**
     * 통과 시각을 Date 객체로 반환
     */
    fun getPassTimeAsDate(): Date {
        return Date(passTime)
    }

    /**
     * 생성 시각을 Date 객체로 반환
     */
    fun getCreatedAtAsDate(): Date {
        return Date(createdAt)
    }

    /**
     * 업데이트 시각을 Date 객체로 반환
     */
    fun getUpdatedAtAsDate(): Date {
        return Date(updatedAt)
    }

    /**
     * 구간 소요 시간을 시간 형식으로 반환 (HH:MM:SS)
     */
    fun getFormattedSegmentDuration(): String {
        val hours = segmentDuration / 3600
        val minutes = (segmentDuration % 3600) / 60
        val seconds = segmentDuration % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    /**
     * 누적 소요 시간을 시간 형식으로 반환 (HH:MM:SS)
     */
    fun getFormattedCumulativeTime(): String {
        val hours = cumulativeTime / 3600
        val minutes = (cumulativeTime % 3600) / 60
        val seconds = cumulativeTime % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    /**
     * 구간 평균 속도 계산 (m/s) - 거리가 주어졌을 때
     */
    fun calculateSegmentSpeed(segmentDistanceMeters: Double): Double {
        return if (segmentDuration > 0) {
            segmentDistanceMeters / segmentDuration
        } else {
            0.0
        }
    }

    /**
     * 구간 평균 페이스 계산 (초/km) - 거리가 주어졌을 때
     */
    fun calculateSegmentPace(segmentDistanceMeters: Double): Double {
        val distanceInKm = segmentDistanceMeters / 1000.0
        return if (distanceInKm > 0) {
            segmentDuration / distanceInKm
        } else {
            0.0
        }
    }

    /**
     * 전체 평균 속도 계산 (m/s) - 총 거리가 주어졌을 때
     */
    fun calculateOverallSpeed(totalDistanceMeters: Double): Double {
        return if (cumulativeTime > 0) {
            totalDistanceMeters / cumulativeTime
        } else {
            0.0
        }
    }

    /**
     * 전체 평균 페이스 계산 (초/km) - 총 거리가 주어졌을 때
     */
    fun calculateOverallPace(totalDistanceMeters: Double): Double {
        val distanceInKm = totalDistanceMeters / 1000.0
        return if (distanceInKm > 0) {
            cumulativeTime / distanceInKm
        } else {
            0.0
        }
    }

    /**
     * 구간 페이스를 MM:SS 형식으로 반환
     */
    fun getFormattedSegmentPace(segmentDistanceMeters: Double): String {
        val paceInSeconds = calculateSegmentPace(segmentDistanceMeters).toInt()
        val minutes = paceInSeconds / 60
        val seconds = paceInSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    /**
     * 전체 페이스를 MM:SS 형식으로 반환
     */
    fun getFormattedOverallPace(totalDistanceMeters: Double): String {
        val paceInSeconds = calculateOverallPace(totalDistanceMeters).toInt()
        val minutes = paceInSeconds / 60
        val seconds = paceInSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    /**
     * 체크포인트 통과 여부 검증
     */
    fun isValidCheckpointPass(): Boolean {
        return passTime > 0 && 
               segmentDuration >= 0 && 
               cumulativeTime >= 0 && 
               cumulativeTime >= segmentDuration &&
               checkpointId.isNotBlank() &&
               participantId > 0
    }

    /**
     * 시작점 체크포인트 여부 (구간 시간이 누적 시간과 같은 경우)
     */
    fun isStartCheckpoint(): Boolean {
        return segmentDuration == cumulativeTime
    }

    /**
     * 시간 기반 성능 비교를 위한 점수 계산
     */
    fun calculatePerformanceScore(): Double {
        // 누적 시간이 적을수록 높은 점수 (역수 사용)
        return if (cumulativeTime > 0) {
            1000000.0 / cumulativeTime
        } else {
            0.0
        }
    }

    /**
     * 데이터 업데이트 시 호출되는 메서드
     */
    @PreUpdate
    fun preUpdate() {
        // updatedAt은 불변이므로 새로운 인스턴스 생성 시에만 설정
    }

    /**
     * 업데이트된 인스턴스 생성
     */
    fun withUpdatedTime(): ParticipantCheckpointTimes {
        return this.copy(updatedAt = System.currentTimeMillis())
    }
} 
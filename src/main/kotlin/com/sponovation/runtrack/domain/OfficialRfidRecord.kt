package com.sponovation.runtrack.domain

import com.sponovation.runtrack.enums.CheckpointType
import com.sponovation.runtrack.enums.CheckpointDetails
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "official_rfid_records",
    indexes = [
        Index(name = "idx_official_rfid_event_participant", columnList = "eventParticipantId"),
        Index(name = "idx_official_rfid_cp_id", columnList = "cpId"),
        Index(name = "idx_official_rfid_pass_time", columnList = "passTime"),
        Index(name = "idx_official_rfid_final_record", columnList = "isFinalRecord"),
        Index(name = "idx_official_rfid_recorded_at", columnList = "recordedAt"),
        Index(name = "idx_official_rfid_participant_cp", columnList = "eventParticipantId,cpId"),
        Index(name = "idx_official_rfid_cumulative_duration", columnList = "cumulativeDuration")
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_participant_cp_record", columnNames = ["eventParticipantId", "cpId", "passTime"])
    ]
)
data class OfficialRfidRecord(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0L,

    @Column(name = "event_participant_id", nullable = false)
    val eventParticipantId: Long,

    @Column(name = "cp_id", length = 50, nullable = false)
    val cpId: String,

    @Column(name = "pass_time", nullable = false)
    val passTime: LocalDateTime,

    @Column(name = "cumulative_duration", nullable = false)
    val cumulativeDuration: Int,

    @Column(name = "finish_time", nullable = true)
    val finishTime: Int? = null,

    @Column(name = "is_final_record", nullable = false)
    val isFinalRecord: Boolean = false,

    @Column(name = "recorded_at", nullable = false)
    val recordedAt: LocalDateTime = LocalDateTime.now()
) {
    
    /**
     * 누적 소요 시간을 시간 형식으로 반환 (HH:MM:SS)
     */
    fun getFormattedCumulativeDuration(): String {
        val hours = cumulativeDuration / 3600
        val minutes = (cumulativeDuration % 3600) / 60
        val seconds = cumulativeDuration % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    /**
     * 완주 시간을 시간 형식으로 반환 (HH:MM:SS)
     */
    fun getFormattedFinishTime(): String? {
        return finishTime?.let { time ->
            val hours = time / 3600
            val minutes = (time % 3600) / 60
            val seconds = time % 60
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        }
    }

    /**
     * 체크포인트 타입 판별 (정교한 파싱)
     */
    fun getCheckpointType(): CheckpointType {
        val cpIdUpper = cpId.uppercase()
        
        return when {
            // 수영 관련 패턴들
            cpIdUpper.matches(Regex(".*SWIM.*")) ||
            cpIdUpper.matches(Regex(".*S\\d+.*")) ||
            cpIdUpper.matches(Regex(".*\\d+-?S(WIM)?.*")) ||
            cpIdUpper.matches(Regex(".*수영.*")) -> CheckpointType.SWIM
            
            // 자전거 관련 패턴들
            cpIdUpper.matches(Regex(".*BIKE.*")) ||
            cpIdUpper.matches(Regex(".*CYCLING.*")) ||
            cpIdUpper.matches(Regex(".*B\\d+.*")) ||
            cpIdUpper.matches(Regex(".*\\d+-?B(IKE)?.*")) ||
            cpIdUpper.matches(Regex(".*자전거.*")) ||
            cpIdUpper.matches(Regex(".*사이클.*")) -> CheckpointType.BIKE
            
            // 달리기 관련 패턴들
            cpIdUpper.matches(Regex(".*RUN.*")) ||
            cpIdUpper.matches(Regex(".*RUNNING.*")) ||
            cpIdUpper.matches(Regex(".*R\\d+.*")) ||
            cpIdUpper.matches(Regex(".*\\d+-?R(UN)?.*")) ||
            cpIdUpper.matches(Regex(".*달리기.*")) ||
            cpIdUpper.matches(Regex(".*러닝.*")) -> CheckpointType.RUN
            
            // 전환구간 1 (수영→자전거)
            cpIdUpper.matches(Regex(".*T1.*")) ||
            cpIdUpper.matches(Regex(".*TRANSITION.*1.*")) ||
            cpIdUpper.matches(Regex(".*전환.*1.*")) ||
            cpIdUpper.matches(Regex(".*T-1.*")) -> CheckpointType.TRANSITION_1
            
            // 전환구간 2 (자전거→달리기)
            cpIdUpper.matches(Regex(".*T2.*")) ||
            cpIdUpper.matches(Regex(".*TRANSITION.*2.*")) ||
            cpIdUpper.matches(Regex(".*전환.*2.*")) ||
            cpIdUpper.matches(Regex(".*T-2.*")) -> CheckpointType.TRANSITION_2
            
            // 완주 관련 패턴들
            cpIdUpper.matches(Regex(".*FINISH.*")) ||
            cpIdUpper.matches(Regex(".*END.*")) ||
            cpIdUpper.matches(Regex(".*GOAL.*")) ||
            cpIdUpper.matches(Regex(".*완주.*")) ||
            cpIdUpper.matches(Regex(".*골.*")) -> CheckpointType.FINISH
            
            // 시작 관련 패턴들
            cpIdUpper.matches(Regex(".*START.*")) ||
            cpIdUpper.matches(Regex(".*BEGIN.*")) ||
            cpIdUpper.matches(Regex(".*시작.*")) -> CheckpointType.START
            
            // 기본값
            else -> CheckpointType.INTERMEDIATE
        }
    }

    /**
     * 체크포인트 번호 추출 (예: "SWIM-01" -> "01", "S02" -> "02")
     */
    fun getCheckpointNumber(): String? {
        val patterns = listOf(
            Regex(".*-(\\d+).*"),           // SWIM-01, BIKE-02 형태
            Regex(".*(\\d+)$"),             // SWIM01, BIKE02 형태
            Regex("^(\\d+)-.*"),            // 01-SWIM, 02-BIKE 형태
            Regex(".*[A-Z](\\d+).*"),       // S01, B02, R03 형태
            Regex(".*CP(\\d+).*"),          // CP01, CP02 형태
            Regex(".*(\\d+).*")             // 일반적인 숫자 추출
        )
        
        for (pattern in patterns) {
            val match = pattern.find(cpId)
            if (match != null) {
                return match.groupValues[1].padStart(2, '0') // 2자리로 패딩
            }
        }
        return null
    }

    /**
     * 체크포인트 순서 번호 (정수형)
     */
    fun getCheckpointSequence(): Int? {
        return getCheckpointNumber()?.toIntOrNull()
    }

    /**
     * 체크포인트 전체 식별자 (타입 + 번호)
     */
    fun getCheckpointIdentifier(): String {
        val type = getCheckpointType()
        val number = getCheckpointNumber()
        return if (number != null) {
            "${type.name}-${number}"
        } else {
            "${type.name}-${cpId}"
        }
    }

    /**
     * 체크포인트 상세 정보 반환
     */
    fun getCheckpointDetails(): CheckpointDetails {
        return CheckpointDetails(
            type = getCheckpointType(),
            number = getCheckpointNumber(),
            sequence = getCheckpointSequence(),
            identifier = getCheckpointIdentifier(),
            originalCpId = cpId
        )
    }

    /**
     * 종목별 체크포인트인지 확인
     */
    fun isSportCheckpoint(): Boolean {
        return getCheckpointType() in listOf(
            CheckpointType.SWIM, 
            CheckpointType.BIKE, 
            CheckpointType.RUN
        )
    }

    /**
     * 전환구간 체크포인트인지 확인
     */
    fun isTransitionCheckpoint(): Boolean {
        return getCheckpointType() in listOf(
            CheckpointType.TRANSITION_1, 
            CheckpointType.TRANSITION_2
        )
    }

    /**
     * 시작점 체크포인트인지 확인
     */
    fun isStartCheckpoint(): Boolean {
        return getCheckpointType() == CheckpointType.START
    }

    /**
     * 완주점 체크포인트인지 확인
     */
    fun isFinishCheckpoint(): Boolean {
        return getCheckpointType() == CheckpointType.FINISH
    }

    /**
     * 중간 체크포인트인지 확인
     */
    fun isIntermediateCheckpoint(): Boolean {
        return getCheckpointType() == CheckpointType.INTERMEDIATE
    }

    /**
     * 기록 상태 반환
     */
    fun getRecordStatus(): String {
        return when {
            isFinalRecord && isFinishCheckpoint() -> "완주"
            isFinalRecord -> "최종 기록"
            isStartCheckpoint() -> "출발"
            isFinishCheckpoint() -> "도착"
            isTransitionCheckpoint() -> "전환"
            isSportCheckpoint() -> "진행"
            else -> "통과"
        }
    }

    /**
     * 평균 속도 계산 (m/s)
     * 
     * @param distance 해당 구간 거리 (미터)
     * @param previousTime 이전 체크포인트 시간 (초)
     * @return 평균 속도 (m/s)
     */
    fun calculateAverageSpeed(distance: Double, previousTime: Int): Double {
        val timeDiff = cumulativeDuration - previousTime
        return if (timeDiff > 0) {
            distance / timeDiff
        } else {
            0.0
        }
    }

    /**
     * 페이스 계산 (분/km)
     * 
     * @param distance 해당 구간 거리 (미터)
     * @param previousTime 이전 체크포인트 시간 (초)
     * @return 페이스 (분/km)
     */
    fun calculatePace(distance: Double, previousTime: Int): Double {
        val timeDiff = cumulativeDuration - previousTime
        val distanceKm = distance / 1000.0
        return if (distanceKm > 0) {
            (timeDiff / 60.0) / distanceKm
        } else {
            0.0
        }
    }

    /**
     * 구간 기록 문자열 반환
     */
    fun getSplitTimeString(distance: Double, previousTime: Int): String {
        val timeDiff = cumulativeDuration - previousTime
        val hours = timeDiff / 3600
        val minutes = (timeDiff % 3600) / 60
        val seconds = timeDiff % 60
        val pace = calculatePace(distance, previousTime)
        
        return if (hours > 0) {
            String.format("%02d:%02d:%02d (%.1f분/km)", hours, minutes, seconds, pace)
        } else {
            String.format("%02d:%02d (%.1f분/km)", minutes, seconds, pace)
        }
    }

    /**
     * 완주 예상 시간 계산
     */
    fun estimateFinishTime(totalDistance: Double, currentDistance: Double): Int? {
        return if (currentDistance > 0 && !isFinalRecord) {
            val averageSpeed = currentDistance / cumulativeDuration
            val remainingDistance = totalDistance - currentDistance
            val estimatedRemainingTime = remainingDistance / averageSpeed
            cumulativeDuration + estimatedRemainingTime.toInt()
        } else {
            null
        }
    }

    /**
     * 완주 예상 시간 문자열 반환
     */
    fun getEstimatedFinishTimeString(totalDistance: Double, currentDistance: Double): String? {
        return estimateFinishTime(totalDistance, currentDistance)?.let { time ->
            val hours = time / 3600
            val minutes = (time % 3600) / 60
            val seconds = time % 60
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        }
    }

    /**
     * 진행률 계산 (%)
     */
    fun calculateProgress(totalDistance: Double, currentDistance: Double): Double {
        return if (totalDistance > 0) {
            (currentDistance / totalDistance) * 100.0
        } else {
            0.0
        }
    }

    /**
     * 상대적 순위 정보 포함 문자열 반환
     */
    fun getDetailedRecordString(
        totalDistance: Double,
        currentDistance: Double,
        rank: Int? = null,
        totalParticipants: Int? = null
    ): String {
        val basicInfo = "${getCheckpointIdentifier()} - ${getFormattedCumulativeDuration()}"
        val progress = calculateProgress(totalDistance, currentDistance)
        val estimatedFinish = getEstimatedFinishTimeString(totalDistance, currentDistance)
        
        val rankInfo = if (rank != null && totalParticipants != null) {
            " (${rank}위/${totalParticipants}명)"
        } else {
            ""
        }
        
        val progressInfo = String.format(" [%.1f%% 완주]", progress)
        val estimatedInfo = estimatedFinish?.let { " 예상완주: $it" } ?: ""
        
        return basicInfo + rankInfo + progressInfo + estimatedInfo
    }
} 
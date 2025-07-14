package com.sponovation.runtrack.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 체크포인트 정보를 저장하는 엔티티
 * 
 * 이 엔티티는 특정 코스의 체크포인트 정보를 관리합니다.
 * 체크포인트는 코스 진행 상황을 추적하고 참가자의 위치를 확인하는 데 사용됩니다.
 * 
 * 주요 기능:
 * - 코스별 체크포인트 위치 정보 관리
 * - 체크포인트 순서 및 명칭 관리
 * - 시작점으로부터의 거리 계산
 * - 참가자 통과 여부 추적
 * 
 * 관계 매핑:
 * - Course: 다대일 관계 (여러 체크포인트가 하나의 코스에 속함)
 * 
 * @see Course 코스 정보
 */
@Entity
@Table(
    name = "checkpoints",
    uniqueConstraints = [
        UniqueConstraint(
            columnNames = ["course_id", "cp_index"],
            name = "uk_course_cp_index"
        ),
        UniqueConstraint(
            columnNames = ["course_id", "cp_id"],
            name = "uk_course_cp_id"
        )
    ]
)
data class Checkpoint(
    /**
     * 체크포인트 고유 식별자 (Primary Key)
     * 자동 증가하는 Long 타입 ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0L,

    /**
     * 코스 ID (Foreign Key)
     * 이 체크포인트가 속한 코스의 고유 식별자
     * 
     * 지연 로딩(LAZY): 필요시에만 코스 정보를 조회
     * 외래키 제약조건으로 데이터 무결성 보장
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    val course: Course,

    /**
     * 체크포인트 명칭
     * 체크포인트의 식별 가능한 이름
     * 
     * 예시:
     * - 'CP1', 'CP2', 'CP3' (중간 체크포인트)
     * - 'START' (시작점)
     * - 'FINISH' (완주점)
     * - 'WATER1', 'WATER2' (급수대)
     */
    @Column(name = "cp_id", nullable = false, length = 50)
    val cpId: String,

    /**
     * 체크포인트 순서
     * 코스에서 체크포인트가 나타나는 순서
     * 
     * 0부터 시작하여 순차적으로 증가
     * 시작점: 0, 첫 번째 체크포인트: 1, 완주점: 마지막 번호
     */
    @Column(name = "cp_index", nullable = false)
    val cpIndex: Int,

    /**
     * 체크포인트 위도
     * WGS84 좌표계 기준, 소수점 이하 8자리 정밀도
     * 높은 정밀도로 정확한 위치 추적 가능
     */
    @Column(name = "latitude", nullable = false, precision = 12, scale = 8)
    val latitude: BigDecimal,

    /**
     * 체크포인트 경도
     * WGS84 좌표계 기준, 소수점 이하 8자리 정밀도
     * 높은 정밀도로 정확한 위치 추적 가능
     */
    @Column(name = "longitude", nullable = false, precision = 12, scale = 8)
    val longitude: BigDecimal,

    /**
     * 체크포인트 고도
     * 해수면 기준 높이 (미터 단위)
     * 소수점 이하 2자리 정밀도
     */
    @Column(name = "altitude", nullable = false, precision = 8, scale = 2)
    val altitude: BigDecimal,

    /**
     * 시작점으로부터의 거리 (미터 단위)
     * 코스 시작점에서 이 체크포인트까지의 누적 거리
     * 
     * 계산 방식:
     * - 경로를 따라 이동한 실제 거리
     * - 직선 거리가 아닌 코스 경로 거리
     * - 참가자의 진행률 계산에 활용
     */
    @Column(name = "distance_from_start", nullable = false, precision = 10, scale = 2)
    val distanceFromStart: BigDecimal,

    /**
     * 생성 일시
     * 체크포인트가 시스템에 등록된 시간
     */
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    /**
     * 마지막 업데이트 일시
     * 체크포인트 정보가 마지막으로 수정된 시간
     */
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    /**
     * 체크포인트가 시작점인지 확인합니다
     * 
     * @return 시작점 여부 (cpIndex가 0이고 distanceFromStart가 0인 경우)
     */
    fun isStartPoint(): Boolean {
        return cpIndex == 0 && distanceFromStart.toDouble() == 0.0
    }

    /**
     * 체크포인트가 완주점인지 확인합니다
     * 
     * @return 완주점 여부 (cpId가 'FINISH'인 경우)
     */
    fun isFinishPoint(): Boolean {
        return cpId.equals("FINISH", ignoreCase = true)
    }

    /**
     * 체크포인트가 중간 지점인지 확인합니다
     * 
     * @return 중간 지점 여부 (시작점도 완주점도 아닌 경우)
     */
    fun isIntermediatePoint(): Boolean {
        return !isStartPoint() && !isFinishPoint()
    }

    /**
     * 체크포인트의 GPS 좌표를 Double 타입으로 반환합니다
     * 
     * @return GPS 좌표 (위도, 경도) 쌍
     */
    fun getCoordinates(): Pair<Double, Double> {
        return Pair(latitude.toDouble(), longitude.toDouble())
    }

    /**
     * 체크포인트까지의 진행률을 계산합니다
     * 
     * @param totalCourseDistance 코스 전체 거리 (미터)
     * @return 진행률 (0.0 ~ 1.0)
     */
    fun getProgressRate(totalCourseDistance: BigDecimal): Double {
        return if (totalCourseDistance.toDouble() > 0) {
            (distanceFromStart.toDouble() / totalCourseDistance.toDouble()).coerceIn(0.0, 1.0)
        } else {
            0.0
        }
    }

    /**
     * 시작점으로부터의 거리를 킬로미터 단위로 반환합니다
     * 
     * @return 거리 (km)
     */
    fun getDistanceFromStartKm(): Double {
        return distanceFromStart.toDouble() / 1000.0
    }

    /**
     * 다른 체크포인트와의 거리를 계산합니다 (Haversine 공식)
     * 
     * @param other 다른 체크포인트
     * @return 두 체크포인트 간의 직선 거리 (미터)
     */
    fun getDistanceTo(other: Checkpoint): Double {
        val lat1 = latitude.toDouble()
        val lng1 = longitude.toDouble()
        val lat2 = other.latitude.toDouble()
        val lng2 = other.longitude.toDouble()
        
        return calculateHaversineDistance(lat1, lng1, lat2, lng2)
    }

    /**
     * 체크포인트 타입을 반환합니다
     * 
     * @return 체크포인트 타입 (START, FINISH, INTERMEDIATE, WATER, etc.)
     */
    fun getCheckpointType(): String {
        return when {
            isStartPoint() -> "START"
            isFinishPoint() -> "FINISH"
            cpId.startsWith("WATER", ignoreCase = true) -> "WATER"
            cpId.startsWith("CP", ignoreCase = true) -> "INTERMEDIATE"
            else -> "OTHER"
        }
    }

    /**
     * 엔티티 업데이트 시 자동으로 updatedAt 필드를 현재 시간으로 설정
     */
    @PreUpdate
    fun onUpdate() {
        updatedAt = LocalDateTime.now()
    }

    /**
     * Haversine 공식을 사용하여 두 지점 간의 거리를 계산합니다
     * 
     * @param lat1 첫 번째 지점의 위도
     * @param lng1 첫 번째 지점의 경도
     * @param lat2 두 번째 지점의 위도
     * @param lng2 두 번째 지점의 경도
     * @return 두 지점 간의 거리 (미터)
     */
    private fun calculateHaversineDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earthRadius = 6371000.0 // 지구 반지름 (미터)
        
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLng / 2) * Math.sin(dLng / 2)
        
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        
        return earthRadius * c
    }
} 

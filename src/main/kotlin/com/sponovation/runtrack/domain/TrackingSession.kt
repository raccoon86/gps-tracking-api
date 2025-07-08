package com.sponovation.runtrack.domain

import com.fasterxml.jackson.annotation.JsonBackReference
import com.fasterxml.jackson.annotation.JsonManagedReference
import jakarta.persistence.*
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * 사용자의 GPS 추적 세션을 관리하는 엔티티
 * 
 * 이 엔티티는 사용자가 특정 경로를 따라 이동하는 전체 세션을 추적합니다.
 * 마라톤 대회 참가, 훈련 세션, 또는 일반적인 경로 추적 등에 사용됩니다.
 * 
 * 세션 생명주기:
 * 1. STARTED: 추적 시작 (사용자가 Start 버튼 클릭)
 * 2. PAUSED: 일시정지 (휴식, 신호등 대기 등)
 * 3. COMPLETED: 정상 완료 (목표 지점 도달)
 * 4. STOPPED: 중도 포기 (사용자가 Stop 버튼 클릭)
 * 
 * 관계 매핑:
 * - GPX 경로: 추적할 미리 정의된 경로 정보
 * - GPS 포인트: 실시간으로 수집되는 위치 데이터
 * - 체크포인트 도달: 경로상 주요 지점 통과 기록
 * 
 * @see GpxRoute 추적 대상 경로
 * @see GpsPoint 수집된 GPS 데이터
 * @see CheckpointReach 체크포인트 통과 기록
 */
@Entity
@Table(name = "tracking_sessions")
data class TrackingSession(
    /** 
     * 고유 식별자 (Primary Key)
     * 데이터베이스에서 자동 생성되는 순차적 세션 ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    /** 
     * 사용자 식별자
     * 이 추적 세션을 소유한 사용자의 고유 ID
     * 
     * 참고: 실제 프로덕션에서는 User 엔티티와의 외래키 관계로 설정하는 것이 좋음
     * 현재는 간단히 문자열로 구현 (예: "user123", "runner_456")
     */
    @Column(nullable = false)
    val userId: String,

    /** 
     * 추적 대상 GPX 경로
     * 사용자가 따라갈 미리 정의된 경로에 대한 참조
     * 
     * 지연 로딩(LAZY): 필요시에만 경로 상세 정보를 조회
     * null 허용: 자유 주행 모드에서는 경로 없이 추적 가능
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gpx_route_id")
    @JsonBackReference("route-sessions")
    val gpxRoute: GpxRoute? = null,

    /** 
     * 현재 추적 상태
     * 세션의 현재 진행 상태를 나타내는 열거형 값
     * 
     * @see TrackingStatus 상태 열거형 정의
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: TrackingStatus,

    /** 
     * 추적 시작 시간
     * 사용자가 추적을 시작한 정확한 시간
     * 경과 시간 계산 및 세션 분석의 기준점
     */
    @Column(nullable = false)
    val startTime: LocalDateTime,

    /** 
     * 추적 종료 시간
     * 추적이 완료되거나 중단된 시간
     * status가 COMPLETED 또는 STOPPED일 때만 값이 존재
     */
    @Column
    val endTime: LocalDateTime? = null,

    /** 
     * 총 이동 거리 (미터 단위)
     * GPS 포인트들 사이의 실제 이동 거리 누적값
     * 
     * 계산 방식:
     * - Haversine 공식을 사용한 구면 거리 계산
     * - 연속된 GPS 포인트 간 거리를 누적 합산
     * - 정확도가 낮은 GPS 포인트는 제외하여 계산
     */
    @Column
    val totalDistance: Double = 0.0,

    /** 
     * 총 소요 시간 (초 단위)
     * 시작부터 완료까지의 실제 이동 시간
     * 
     * 계산 방식:
     * - 순수 이동 시간만 계산 (PAUSED 상태 제외)
     * - startTime과 endTime의 차이에서 일시정지 시간 차감
     * - 실시간 업데이트: 진행 중에도 현재까지의 소요 시간 계산 가능
     */
    @Column
    val totalDuration: Long = 0, // seconds

    /** 
     * 평균 속도 (m/s 단위)
     * 전체 세션 동안의 평균 이동 속도
     * 
     * 계산 공식: totalDistance / totalDuration
     * 
     * 속도 변환 참고:
     * - 1 m/s = 3.6 km/h
     * - 일반적인 걷기 속도: 1.4 m/s (5 km/h)
     * - 일반적인 조깅 속도: 2.8 m/s (10 km/h)
     */
    @Column
    val averageSpeed: Double = 0.0,

    /** 
     * 최고 속도 (m/s 단위)
     * 세션 중 기록된 순간 최고 속도
     * 
     * 주의사항:
     * - GPS 오차로 인한 순간적 높은 속도는 필터링 필요
     * - 일반적으로 연속된 3개 이상의 GPS 포인트에서 일정 속도 이상일 때만 유효 처리
     */
    @Column
    val maxSpeed: Double = 0.0,

    /** 
     * 총 상승 고도 (미터 단위)
     * 세션 중 상승한 총 고도의 합
     * 
     * 계산 방식:
     * - 연속된 GPS 포인트 간 고도 차이 계산
     * - 상승한 경우만 누적 (하강은 제외)
     * - GPS 고도 오차 보정을 위해 일정 임계값(예: 2m) 이상 변화만 반영
     * 
     * 활용: 운동 강도 측정, 칼로리 계산, 코스 난이도 평가
     */
    @Column
    val totalElevationGain: Double = 0.0,

    /** 
     * 수집된 GPS 포인트 목록
     * 이 세션 동안 수집된 모든 GPS 위치 데이터
     * 
     * 관계 설정:
     * - One-to-Many: 하나의 세션에 여러 GPS 포인트
     * - Cascade ALL: 세션 삭제 시 관련 GPS 포인트도 함께 삭제
     * - 지연 로딩: 성능 최적화를 위해 필요시에만 로드
     */
    @OneToMany(mappedBy = "trackingSession", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    @JsonManagedReference("session-gps")
    val gpsPoints: List<GpsPoint> = emptyList(),

    /** 
     * 체크포인트 도달 기록 목록
     * 경로상의 주요 지점 통과 시간과 상세 정보
     * 
     * 관계 설정:
     * - One-to-Many: 하나의 세션에 여러 체크포인트 통과 기록
     * - Cascade ALL: 세션 삭제 시 관련 도달 기록도 함께 삭제
     * - 지연 로딩: 성능 최적화를 위해 필요시에만 로드
     */
    @OneToMany(mappedBy = "trackingSession", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    @JsonManagedReference("session-reaches")
    val checkpointReaches: List<CheckpointReach> = emptyList()
) {
    /**
     * 현재까지의 경과 시간을 계산합니다 (초 단위)
     * 세션이 진행 중인 경우 현재 시간까지, 완료된 경우 전체 시간을 반환
     */
    fun getElapsedTimeSeconds(): Long {
        val endTimeToUse = endTime ?: LocalDateTime.now()
        return ChronoUnit.SECONDS.between(startTime, endTimeToUse)
    }
    
    /**
     * 세션이 활성 상태인지 확인합니다
     * STARTED 또는 PAUSED 상태일 때 true 반환
     */
    fun isActive(): Boolean {
        return status == TrackingStatus.STARTED || status == TrackingStatus.PAUSED
    }
    
    /**
     * 세션이 완료되었는지 확인합니다
     * COMPLETED 또는 STOPPED 상태일 때 true 반환
     */
    fun isFinished(): Boolean {
        return status == TrackingStatus.COMPLETED || status == TrackingStatus.STOPPED
    }
    
    /**
     * 현재 평균 속도를 계산합니다 (km/h 단위)
     * 실시간으로 업데이트되는 평균 속도를 반환
     */
    fun getCurrentAverageSpeedKmh(): Double {
        val elapsedSeconds = getElapsedTimeSeconds()
        return if (elapsedSeconds > 0) {
            (totalDistance / elapsedSeconds) * 3.6 // m/s to km/h
        } else {
            0.0
        }
    }
    
    /**
     * 경로 진행률을 계산합니다 (0.0 ~ 1.0)
     * GPX 경로가 설정된 경우 전체 경로 대비 현재 위치의 진행률 반환
     */
    fun getRouteProgress(): Double {
        return gpxRoute?.let { route ->
            if (route.totalDistance > 0) {
                (totalDistance / route.totalDistance).coerceIn(0.0, 1.0)
            } else {
                0.0
            }
        } ?: 0.0
    }
}

/**
 * 추적 세션의 상태를 나타내는 열거형
 * 
 * 각 상태별 의미:
 * - STARTED: 추적 진행 중 (GPS 데이터 수집 중)
 * - PAUSED: 일시정지 (GPS 수집 중단, 사용자 요청 또는 신호 손실)
 * - COMPLETED: 정상 완료 (목표 지점 도달 또는 경로 완주)
 * - STOPPED: 중도 중단 (사용자가 직접 중단하거나 오류로 인한 종료)
 */
enum class TrackingStatus {
    /** 추적 시작됨 - GPS 데이터 수집 중 */
    STARTED,
    
    /** 일시정지 - GPS 수집 중단됨 */
    PAUSED,
    
    /** 정상 완료 - 목표 달성 */
    COMPLETED,
    
    /** 중도 중단 - 사용자 요청 또는 오류 */
    STOPPED
} 

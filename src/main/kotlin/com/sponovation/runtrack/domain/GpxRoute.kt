package com.sponovation.runtrack.domain

import com.fasterxml.jackson.annotation.JsonManagedReference
import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * GPX 경로 정보를 저장하는 엔티티
 * 
 * GPX(GPS Exchange Format) 파일에서 추출한 경로 데이터를 저장하며,
 * 마라톤 대회 코스, 훈련 경로, 관광 루트 등에 활용됩니다.
 * 
 * 주요 기능:
 * - GPX 파일 파싱을 통한 경로 데이터 자동 생성
 * - 웨이포인트 및 체크포인트 자동 배치
 * - 경로 통계 정보 자동 계산
 * - 실시간 추적 세션과의 연계
 * 
 * @see GpxWaypoint 경로 구성 포인트
 * @see Checkpoint 체크포인트 정보
 * @see TrackingSession 추적 세션 연결
 */
@Entity
@Table(name = "gpx_routes")
data class GpxRoute(
    /** 고유 식별자 (Primary Key) */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    /** 
     * 경로 이름
     * 사용자가 지정한 경로의 표시명
     * 예: "한강 러닝 코스", "북한산 등산로", "제주 올레길 1코스"
     */
    @Column(nullable = false)
    val name: String,

    /** 
     * 경로 설명
     * 경로에 대한 상세 설명 및 특이사항
     * 난이도, 주의사항, 볼거리 등의 정보 포함
     */
    @Column(nullable = false)
    val description: String,

    /** 
     * 총 거리 (미터 단위)
     * GPX 파일의 모든 웨이포인트를 연결한 총 경로 길이
     * Haversine 공식을 사용하여 정확한 거리 계산
     */
    @Column(nullable = false)
    val totalDistance: Double,

    /** 
     * 총 상승 고도 (미터 단위)
     * 경로상에서 상승하는 고도의 총합
     * 경로 난이도 및 칼로리 소모량 계산에 활용
     */
    @Column(nullable = false)
    val totalElevationGain: Double,

    /** 
     * 총 하강 고도 (미터 단위)
     * 경로상에서 하강하는 고도의 총합
     * 무릎 부담 등 안전성 평가에 활용
     */
    @Column(nullable = false)
    val totalElevationLoss: Double,

    /** 
     * 경로 생성 시간
     * GPX 파일이 업로드되고 처리된 시간
     */
    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    /** 
     * 경로 구성 웨이포인트 목록
     * GPX 파일에서 추출한 모든 위치 점들의 순서대로 정렬된 목록
     */
    @OneToMany(mappedBy = "gpxRoute", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    @JsonManagedReference("route-waypoints")
    val waypoints: List<GpxWaypoint> = emptyList(),

    /** 
     * 체크포인트 목록
     * 1km 간격으로 자동 생성되는 주요 지점들
     * 진행률 추적 및 알림에 활용
     */
    @OneToMany(mappedBy = "gpxRoute", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    @JsonManagedReference("route-checkpoints")
    val checkpoints: List<Checkpoint> = emptyList(),

    /** 
     * 관련 추적 세션 목록
     * 이 경로를 사용하여 진행된 모든 추적 세션들
     */
    @OneToMany(mappedBy = "gpxRoute", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    @JsonManagedReference("route-sessions")
    val trackingSessions: List<TrackingSession> = emptyList()
) {
    /**
     * 경로 길이를 킬로미터 단위로 반환
     */
    fun getTotalDistanceKm(): Double = totalDistance / 1000.0
    
    /**
     * 예상 소요 시간을 계산 (분 단위)
     * 평균 걷기 속도 5km/h 기준
     */
    fun getEstimatedDurationMinutes(): Double = getTotalDistanceKm() / 5.0 * 60.0
} 

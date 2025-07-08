package com.sponovation.runtrack.domain

import com.fasterxml.jackson.annotation.JsonBackReference
import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * GPS 위치 정보를 저장하는 엔티티
 * 
 * 이 엔티티는 사용자의 실시간 GPS 데이터를 저장하며, 원본 GPS 신호와
 * 칼만 필터를 통해 보정된 위치 정보를 모두 관리합니다.
 * 
 * 데이터 구조:
 * - 원본 GPS 데이터: 디바이스에서 직접 수신한 raw GPS 정보
 * - 보정된 데이터: 칼만 필터 및 맵 매칭을 통해 정제된 위치 정보
 * - 메타데이터: 정확도, 속도, 방향 등의 부가 정보
 * 
 * 활용 사례:
 * - 실시간 위치 추적 및 경로 기록
 * - GPS 신호 품질 분석 및 오차 보정
 * - 이동 패턴 분석 및 통계 생성
 * - 경로 이탈 감지 및 알림
 * 
 * @see TrackingSession 추적 세션과의 관계
 * @see com.sponovation.runtrack.algorithm.KalmanFilter GPS 보정 알고리즘
 */
@Entity
@Table(name = "gps_points")
data class GpsPoint(
    /** 
     * 고유 식별자 (Primary Key)
     * 데이터베이스에서 자동 생성되는 순차적 ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    /** 
     * 위도 (Latitude)
     * WGS84 좌표계 기준, -90.0 ~ +90.0 범위
     * 북위는 양수, 남위는 음수로 표현
     * 예: 37.5665 (서울 시청 위도)
     */
    @Column(nullable = false)
    val latitude: Double,

    /** 
     * 경도 (Longitude)
     * WGS84 좌표계 기준, -180.0 ~ +180.0 범위
     * 동경은 양수, 서경은 음수로 표현
     * 예: 126.9780 (서울 시청 경도)
     */
    @Column(nullable = false)
    val longitude: Double,

    /** 
     * 고도 (Altitude)
     * 해수면 기준 높이 (미터 단위)
     * GPS 기본 제공 고도는 타원체 고도이므로 실제 해발고도와 차이 있음
     * 일반적으로 ±10m 내외의 오차 발생
     */
    @Column(nullable = false)
    val altitude: Double,

    /** 
     * GPS 신호 정확도 (미터 단위)
     * 해당 GPS 위치의 신뢰도를 나타내는 지표
     * 값이 작을수록 더 정확한 위치를 의미
     * 
     * 일반적인 정확도 수준:
     * - 1-3m: 매우 좋음 (개활지, 좋은 날씨)
     * - 3-5m: 좋음 (일반적인 야외 환경)
     * - 5-10m: 보통 (건물 근처, 나무가 있는 곳)
     * - 10m 이상: 나쁨 (실내, 터널, 고층 건물 사이)
     */
    @Column(nullable = false)
    val accuracy: Float,

    /** 
     * 이동 속도 (m/s 단위)
     * GPS에서 계산된 순간 이동 속도
     * 
     * 속도 변환 참고:
     * - 1 m/s ≈ 3.6 km/h
     * - 걷기: 1.4 m/s (5 km/h)
     * - 조깅: 2.8 m/s (10 km/h)
     * - 달리기: 4.2 m/s (15 km/h)
     */
    @Column(nullable = false)
    val speed: Float,

    /** 
     * 이동 방향 (Bearing/방위각)
     * 북쪽을 기준으로 시계방향 각도 (0-360도)
     * 
     * 방향 참고:
     * - 0/360도: 북쪽 (North)
     * - 90도: 동쪽 (East)
     * - 180도: 남쪽 (South)
     * - 270도: 서쪽 (West)
     */
    @Column(nullable = false)
    val bearing: Float,

    /** 
     * GPS 데이터 수신 시간
     * 디바이스에서 GPS 신호를 수신한 정확한 시간
     * 서버 저장 시간이 아닌 실제 측정 시간을 기록
     */
    @Column(nullable = false)
    val timestamp: LocalDateTime,

    /** 
     * 소속 추적 세션
     * 이 GPS 포인트가 속한 추적 세션에 대한 외래키 참조
     * 지연 로딩(LAZY)을 통해 필요시에만 세션 정보를 조회
     * JSON 직렬화 시 순환 참조 방지를 위해 @JsonBackReference 사용
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tracking_session_id")
    @JsonBackReference("session-gps")
    val trackingSession: TrackingSession? = null,

    /** 
     * 필터링 적용 여부
     * 이 GPS 포인트에 칼만 필터 등의 보정이 적용되었는지 여부
     * true: 보정됨, false: 원본 상태
     */
    @Column(nullable = false)
    val isFiltered: Boolean = false,

    /** 
     * 보정된 위도
     * 칼만 필터를 통해 노이즈가 제거된 보정된 위도 값
     * isFiltered가 true일 때만 값이 존재
     * 원본 latitude 대신 실제 위치 표시에 사용
     */
    @Column
    val filteredLatitude: Double? = null,

    /** 
     * 보정된 경도
     * 칼만 필터를 통해 노이즈가 제거된 보정된 경도 값
     * isFiltered가 true일 때만 값이 존재
     * 원본 longitude 대신 실제 위치 표시에 사용
     */
    @Column
    val filteredLongitude: Double? = null
) {
    /**
     * 실제 사용할 위도 반환
     * 보정된 위도가 있으면 보정된 값, 없으면 원본 값 반환
     */
    fun getEffectiveLatitude(): Double = filteredLatitude ?: latitude
    
    /**
     * 실제 사용할 경도 반환
     * 보정된 경도가 있으면 보정된 값, 없으면 원본 값 반환
     */
    fun getEffectiveLongitude(): Double = filteredLongitude ?: longitude
    
    /**
     * GPS 신호 품질 평가
     * 정확도를 기반으로 신호 품질을 5단계로 분류
     */
    fun getSignalQuality(): String {
        return when {
            accuracy <= 3.0f -> "EXCELLENT"  // 매우 좋음
            accuracy <= 5.0f -> "GOOD"       // 좋음
            accuracy <= 10.0f -> "FAIR"      // 보통
            accuracy <= 20.0f -> "POOR"      // 나쁨
            else -> "VERY_POOR"              // 매우 나쁨
        }
    }
} 

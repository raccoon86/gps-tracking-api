package com.sponovation.runtrack.dto

import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.Min

/**
 * GPS 데이터 전송 DTO
 * 
 * 클라이언트(모바일 앱)에서 서버로 GPS 데이터를 전송할 때 사용하는 데이터 전송 객체입니다.
 * 실시간 위치 추적에 필요한 모든 GPS 정보를 포함하며, 유효성 검증을 통해 데이터 품질을 보장합니다.
 * 
 * 사용 시나리오:
 * - 마라톤 대회 중 참가자의 실시간 위치 전송
 * - 훈련 세션 중 경로 기록
 * - 네비게이션 시 현재 위치 업데이트
 * 
 * @see GpsDataResponse GPS 데이터 처리 결과 응답
 */
data class GpsDataDto(
    @field:NotNull(message = "latitude는 필수입니다.")
    @field:DecimalMin(value = "-90.0", message = "latitude는 -90 이상이어야 합니다.")
    @field:DecimalMax(value = "90.0", message = "latitude는 90 이하여야 합니다.")
    val latitude: Double?,

    @field:NotNull(message = "longitude는 필수입니다.")
    @field:DecimalMin(value = "-180.0", message = "longitude는 -180 이상이어야 합니다.")
    @field:DecimalMax(value = "180.0", message = "longitude는 180 이하여야 합니다.")
    val longitude: Double?,

    /** 
     * 고도 (Altitude)
     * 해수면 기준 높이 (미터 단위)
     * 필수 값: 고도 변화 추적 및 칼로리 계산에 활용
     */
    @field:NotNull
    val altitude: Double,

    @field:NotNull(message = "timestamp는 필수입니다.")
    @field:Min(value = 0, message = "timestamp는 0 이상이어야 합니다.")
    val timestamp: Long?,

    /** 
     * GPS 신호 정확도 (미터 단위)
     * 해당 GPS 위치의 신뢰도를 나타내는 지표
     * 필수 값: 데이터 품질 평가 및 필터링에 사용
     * 
     * 일반적인 정확도 기준:
     * - 1-5m: 높은 정확도 (개활지)
     * - 5-10m: 보통 정확도 (일반 야외)
     * - 10m 이상: 낮은 정확도 (실내, 터널)
     */
    @field:NotNull(message = "accuracy는 필수입니다.")
    @field:DecimalMin(value = "0.0", message = "accuracy는 0 이상이어야 합니다.")
    val accuracy: Double?,

    /** 
     * 이동 속도 (m/s 단위)
     * GPS에서 계산된 순간 이동 속도
     * 필수 값: 운동 강도 측정 및 페이스 계산에 활용
     */
    @field:NotNull(message = "speed는 필수입니다.")
    @field:DecimalMin(value = "0.0", message = "speed는 0 이상이어야 합니다.")
    val speed: Double?,

    /** 
     * 이동 방향 (방위각, 0-360도)
     * 북쪽을 기준으로 시계방향 각도
     * 필수 값: 경로 매칭 및 방향 보정에 활용
     */
    @field:NotNull
    val bearing: Float,

    /** 
     * 추적 세션 ID
     * 이 GPS 데이터가 속한 추적 세션의 식별자
     * 필수 값: 데이터 그룹핑 및 세션 관리
     */
    @field:NotNull
    val sessionId: Long
)

/**
 * GPS 데이터 처리 결과 응답 DTO
 * 
 * 서버에서 GPS 데이터를 처리한 후 클라이언트에게 보내는 응답 정보입니다.
 * 처리 성공 여부, 보정 결과, 경로 매칭 정보 등을 포함합니다.
 * 
 * 응답 구조:
 * - 기본 정보: 성공 여부, 메시지
 * - 매칭 정보: 경로 매칭 성공 여부, 경로로부터의 거리
 * - 체크포인트: 가장 가까운 체크포인트 정보
 * - 위치 정보: 보정된 위치 및 원본 위치
 */
data class GpsDataResponse(
    /** 처리 성공 여부 */
    val success: Boolean,
    
    /** 처리 결과 메시지 (성공/실패 원인) */
    val message: String,
    
    /** 경로 매칭 성공 여부 (GPS가 정의된 경로에 매칭되었는지) */
    val matchedToRoute: Boolean = false,
    
    /** 경로로부터의 거리 (미터 단위, 경로 이탈 감지용) */
    val distanceFromRoute: Double? = null,
    
    /** 가장 가까운 체크포인트 정보 */
    val nearestCheckpoint: CheckpointInfo? = null,
    
    /** 칼만 필터 및 맵 매칭을 통해 보정된 위치 */
    val correctedPosition: CorrectedGpsPosition? = null,
    
    /** 디바이스에서 수신한 원본 GPS 위치 */
    val originalPosition: OriginalGpsPosition? = null
)

/**
 * 보정된 GPS 위치 정보
 * 
 * 칼만 필터와 맵 매칭 알고리즘을 통해 노이즈가 제거되고
 * 경로에 최적화된 정확한 좌표 정보입니다.
 * 
 * 보정 과정:
 * 1. 칼만 필터를 통한 GPS 신호 노이즈 제거
 * 2. 맵 매칭을 통한 경로 상의 최적 위치 계산
 * 3. 방향 정보를 고려한 위치 보정
 * 
 * 활용 목적:
 * - 정확한 위치 표시 (지도상 점 표시)
 * - 경로 진행률 계산
 * - 체크포인트 도달 판정
 */
data class CorrectedGpsPosition(
    /** 보정된 위도 */
    val latitude: Double,
    
    /** 보정된 경도 */
    val longitude: Double,
    
    /** 보정된 고도 (선택적) */
    val altitude: Double? = null,
    
    /** 보정 후 예상 정확도 */
    val accuracy: Float,
    
    /** 보정된 속도 */
    val speed: Float,
    
    /** 보정된 방향 */
    val bearing: Float
)

/**
 * 원본 GPS 위치 정보
 * 
 * 디바이스에서 직접 수신한 가공되지 않은 GPS 데이터입니다.
 * 보정 전후 비교 및 디버깅 목적으로 사용됩니다.
 * 
 * 특징:
 * - GPS 수신기에서 직접 제공하는 raw 데이터
 * - 노이즈 및 오차 포함 가능
 * - 보정 알고리즘의 입력 데이터
 */
data class OriginalGpsPosition(
    /** 원본 위도 */
    val latitude: Double,
    
    /** 원본 경도 */
    val longitude: Double,
    
    /** 원본 고도 */
    val altitude: Double,
    
    /** GPS 수신기에서 제공하는 정확도 */
    val accuracy: Float,
    
    /** GPS에서 계산된 속도 */
    val speed: Float,
    
    /** GPS에서 계산된 방향 */
    val bearing: Float
)

/**
 * 체크포인트 정보 DTO
 * 
 * 경로상의 주요 지점(체크포인트)에 대한 정보를 담는 데이터 객체입니다.
 * 사용자의 진행 상황 확인 및 알림 기능에 활용됩니다.
 * 
 * 체크포인트 특징:
 * - 일반적으로 1km 간격으로 자동 생성
 * - 경로의 주요 변곡점이나 랜드마크에 배치
 * - 진행률 표시 및 완주 인증에 활용
 */
data class CheckpointInfo(
    /** 체크포인트 고유 ID */
    val id: Long,
    
    /** 체크포인트 이름 (예: "1km 지점", "한강대교") */
    val name: String,
    
    /** 시작점으로부터의 거리 (미터 단위) */
    val distance: Double,
    
    /** 도달 여부 (사용자가 이미 통과했는지) */
    val reached: Boolean = false
) 

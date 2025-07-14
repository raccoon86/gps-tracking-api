package com.sponovation.runtrack.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 코스 정보를 저장하는 엔티티
 * 
 * 이 엔티티는 이벤트에 속한 각 코스의 정보를 관리합니다.
 * 하나의 이벤트는 여러 코스를 가질 수 있습니다 (예: 10km, 하프 마라톤, 풀 마라톤).
 * 
 * 주요 기능:
 * - 코스별 GPX 파일 URL 및 메타데이터 관리
 * - 코스 시작/종료 지점 좌표 저장
 * - 코스 거리 및 카테고리 정보 관리
 * - GPX 파일 무결성 검증을 위한 해시 저장
 * 
 * 관계 매핑:
 * - Event: 다대일 관계 (여러 코스가 하나의 이벤트에 속함)
 * 
 * @see Event 이벤트 정보
 */
@Entity
@Table(name = "courses")
data class Course(
    /**
     * 코스 고유 식별자 (Primary Key)
     * Long을 사용하여 자동 증가 식별자 보장
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val courseId: Long = 0L,

    /**
     * 이벤트 ID (Foreign Key)
     * 이 코스가 속한 이벤트의 고유 식별자
     */
    @Column(name = "event_id", nullable = false)
    val eventId: Long,

    /**
     * 이벤트 ID (Foreign Key)
     * 이 코스가 속한 이벤트의 고유 식별자
     */
    @Column(name = "event_detail_id", nullable = false)
    val eventDetailId: Long,

    /**
     * 코스 이름
     * 사용자에게 표시되는 코스의 명칭
     * 예: "하프 마라톤", "10km 코스", "풀 마라톤"
     */
    @Column(name = "course_name", nullable = false, length = 200)
    val courseName: String,

    /**
     * 코스 총 거리 (킬로미터)
     * 소수점 이하 정밀도를 위해 BigDecimal 사용
     * 예: 10.5km, 21.0975km (하프 마라톤), 42.195km (풀 마라톤)
     */
    @Column(name = "distance_km", nullable = false, precision = 10, scale = 4)
    val distanceKm: BigDecimal,

    /**
     * 종목 카테고리
     * 코스의 분류를 나타내는 카테고리명
     * 예: "러닝 10km", "마라톤", "워킹 5km"
     */
    @Column(name = "category_name", nullable = false, length = 100)
    val categoryName: String,

    /**
     * GPX 파일의 S3 URL
     * 아마존 S3에 저장된 GPX 파일의 전체 URL
     * 실시간 위치 보정 및 코스 데이터 조회에 사용
     */
    @Column(name = "gpx_file", nullable = false, columnDefinition = "TEXT")
    val gpxFileUrl: String,

    /**
     * GPX 파일 내용의 해시
     * 파일 무결성 검증 및 변경 감지를 위한 해시 값
     * SHA-256 등의 해시 알고리즘 사용 권장
     */
    @Column(name = "gpx_data_hash", nullable = false, columnDefinition = "TEXT")
    val gpxDataHash: String,

    /**
     * 코스 시작점 위도
     * GPX 파일에서 추출된 코스 시작 지점의 위도
     * WGS84 좌표계 기준, 소수점 이하 6자리 정밀도
     */
    @Column(name = "start_point_lat", nullable = false, precision = 10, scale = 6)
    val startPointLat: BigDecimal,

    /**
     * 코스 시작점 경도
     * GPX 파일에서 추출된 코스 시작 지점의 경도
     * WGS84 좌표계 기준, 소수점 이하 6자리 정밀도
     */
    @Column(name = "start_point_lng", nullable = false, precision = 10, scale = 6)
    val startPointLng: BigDecimal,

    /**
     * 코스 종료점 위도
     * GPX 파일에서 추출된 코스 종료 지점의 위도
     * WGS84 좌표계 기준, 소수점 이하 6자리 정밀도
     */
    @Column(name = "end_point_lat", nullable = false, precision = 10, scale = 6)
    val endPointLat: BigDecimal,

    /**
     * 코스 종료점 경도
     * GPX 파일에서 추출된 코스 종료 지점의 경도
     * WGS84 좌표계 기준, 소수점 이하 6자리 정밀도
     */
    @Column(name = "end_point_lng", nullable = false, precision = 10, scale = 6)
    val endPointLng: BigDecimal,

    /**
     * 생성 일시
     * 코스가 시스템에 등록된 시간
     */
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    /**
     * 마지막 업데이트 일시
     * 코스 정보가 마지막으로 수정된 시간
     */
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    /**
     * 시작점과 종료점 간의 직선 거리를 계산합니다 (미터 단위)
     * 
     * @return 시작점과 종료점 간의 Haversine 거리 (미터)
     */
    fun getStartToEndDistance(): Double {
        val startLat = startPointLat.toDouble()
        val startLng = startPointLng.toDouble()
        val endLat = endPointLat.toDouble()
        val endLng = endPointLng.toDouble()
        
        return calculateHaversineDistance(startLat, startLng, endLat, endLng)
    }

    /**
     * 코스가 루프 코스인지 확인합니다
     * 시작점과 종료점이 100미터 이내에 있으면 루프 코스로 간주
     * 
     * @return 루프 코스 여부
     */
    fun isLoopCourse(): Boolean {
        return getStartToEndDistance() <= 100.0
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
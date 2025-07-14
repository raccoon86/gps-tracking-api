package com.sponovation.runtrack.repository

import com.sponovation.runtrack.domain.Checkpoint
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 체크포인트 데이터 접근을 위한 Repository 인터페이스
 * 
 * 체크포인트 관련 CRUD 연산과 커스텀 쿼리를 제공합니다.
 * Spring Data JPA를 사용하여 기본적인 데이터베이스 연산을 자동화하고,
 * 체크포인트 관리 및 위치 기반 검색을 위한 복잡한 쿼리를 추가 정의합니다.
 */
@Repository
interface CheckpointRepository : JpaRepository<Checkpoint, Long> {
    
    /**
     * 특정 코스의 모든 체크포인트를 순서대로 조회합니다
     * 
     * @param courseId 조회할 코스 ID
     * @return 코스의 체크포인트 목록 (순서대로 정렬)
     */
    fun findByCourse_CourseIdOrderByCpIndexAsc(courseId: Long): List<Checkpoint>
    
    /**
     * 특정 코스의 체크포인트를 페이징하여 조회합니다
     * 
     * @param courseId 조회할 코스 ID
     * @param pageable 페이징 정보
     * @return 코스의 체크포인트 페이지
     */
    fun findByCourse_CourseIdOrderByCpIndexAsc(courseId: Long, pageable: Pageable): Page<Checkpoint>
    
    /**
     * 체크포인트 ID로 특정 체크포인트를 조회합니다
     * 
     * @param courseId 코스 ID
     * @param cpId 체크포인트 ID
     * @return 해당 체크포인트
     */
    fun findByCourse_CourseIdAndCpId(courseId: Long, cpId: String): Checkpoint?
    
    /**
     * 체크포인트 인덱스로 특정 체크포인트를 조회합니다
     * 
     * @param courseId 코스 ID
     * @param cpIndex 체크포인트 인덱스
     * @return 해당 체크포인트
     */
    fun findByCourse_CourseIdAndCpIndex(courseId: Long, cpIndex: Int): Checkpoint?
    
    /**
     * 특정 코스의 시작점을 조회합니다
     * 
     * @param courseId 코스 ID
     * @return 시작점 체크포인트 (cpIndex가 0인 것)
     */
    @Query("""
        SELECT c FROM Checkpoint c 
        WHERE c.course.courseId = :courseId 
        AND c.cpIndex = 0
    """)
    fun findStartPointByCourseId(@Param("courseId") courseId: Long): Checkpoint?
    
    /**
     * 특정 코스의 완주점을 조회합니다
     * 
     * @param courseId 코스 ID
     * @return 완주점 체크포인트 (cpId가 'FINISH'인 것)
     */
    fun findByCourse_CourseIdAndCpIdIgnoreCase(courseId: Long, cpId: String): Checkpoint?
    
    /**
     * 특정 코스의 체크포인트 수를 조회합니다
     * 
     * @param courseId 코스 ID
     * @return 체크포인트 수
     */
    fun countByCourse_CourseId(courseId: Long): Long
    
    /**
     * 특정 거리 범위 내의 체크포인트를 조회합니다
     * 
     * @param courseId 코스 ID
     * @param minDistance 최소 거리
     * @param maxDistance 최대 거리
     * @return 거리 범위 내의 체크포인트 목록
     */
    fun findByCourse_CourseIdAndDistanceFromStartBetweenOrderByCpIndexAsc(
        courseId: Long,
        minDistance: BigDecimal,
        maxDistance: BigDecimal
    ): List<Checkpoint>
    
    /**
     * 특정 위치 주변의 체크포인트를 조회합니다
     * 
     * @param centerLat 중심 위도
     * @param centerLng 중심 경도
     * @param radiusKm 반경 (km)
     * @return 반경 내의 체크포인트 목록
     */
    @Query("""
        SELECT c FROM Checkpoint c 
        WHERE (
            6371 * acos(
                cos(radians(:centerLat)) * cos(radians(c.latitude)) * 
                cos(radians(c.longitude) - radians(:centerLng)) + 
                sin(radians(:centerLat)) * sin(radians(c.latitude))
            )
        ) <= :radiusKm
        ORDER BY (
            6371 * acos(
                cos(radians(:centerLat)) * cos(radians(c.latitude)) * 
                cos(radians(c.longitude) - radians(:centerLng)) + 
                sin(radians(:centerLat)) * sin(radians(c.latitude))
            )
        ) ASC
    """)
    fun findNearbyCheckpoints(
        @Param("centerLat") centerLat: BigDecimal,
        @Param("centerLng") centerLng: BigDecimal,
        @Param("radiusKm") radiusKm: Double
    ): List<Checkpoint>
    
    /**
     * 특정 코스의 특정 거리 이후의 첫 번째 체크포인트를 조회합니다
     * 
     * @param courseId 코스 ID
     * @param distance 기준 거리
     * @return 해당 거리 이후의 첫 번째 체크포인트
     */
    fun findFirstByCourse_CourseIdAndDistanceFromStartGreaterThanOrderByCpIndexAsc(
        courseId: Long,
        distance: BigDecimal
    ): Checkpoint?
    
    /**
     * 특정 코스의 특정 거리 이전의 마지막 체크포인트를 조회합니다
     * 
     * @param courseId 코스 ID
     * @param distance 기준 거리
     * @return 해당 거리 이전의 마지막 체크포인트
     */
    fun findFirstByCourse_CourseIdAndDistanceFromStartLessThanOrderByCpIndexDesc(
        courseId: Long,
        distance: BigDecimal
    ): Checkpoint?
    
    /**
     * 특정 코스의 최대 체크포인트 인덱스를 조회합니다
     * 
     * @param courseId 코스 ID
     * @return 최대 체크포인트 인덱스
     */
    @Query("""
        SELECT MAX(c.cpIndex) 
        FROM Checkpoint c 
        WHERE c.course.courseId = :courseId
    """)
    fun findMaxCpIndexByCourseId(@Param("courseId") courseId: Long): Int?
    
    /**
     * 특정 코스의 총 거리를 조회합니다 (마지막 체크포인트의 거리)
     * 
     * @param courseId 코스 ID
     * @return 코스 총 거리
     */
    @Query("""
        SELECT MAX(c.distanceFromStart) 
        FROM Checkpoint c 
        WHERE c.course.courseId = :courseId
    """)
    fun findTotalDistanceByCourseId(@Param("courseId") courseId: Long): BigDecimal?
    
    /**
     * 특정 이벤트의 모든 체크포인트를 조회합니다
     * 
     * @param eventId 이벤트 ID
     * @return 이벤트의 모든 체크포인트 목록
     */
    @Query("""
        SELECT c FROM Checkpoint c 
        JOIN c.course co 
        WHERE co.eventId = :eventId 
        ORDER BY co.courseName ASC, c.cpIndex ASC
    """)
    fun findByEventIdOrderByCourseAndIndex(
        @Param("eventId") eventId: Long
    ): List<Checkpoint>
    
    /**
     * 특정 체크포인트 ID가 특정 코스에서 이미 사용 중인지 확인합니다
     * 
     * @param courseId 코스 ID
     * @param cpId 체크포인트 ID
     * @return 사용 중인지 여부
     */
    fun existsByCourse_CourseIdAndCpId(courseId: Long, cpId: String): Boolean
    
    /**
     * 특정 체크포인트 인덱스가 특정 코스에서 이미 사용 중인지 확인합니다
     * 
     * @param courseId 코스 ID
     * @param cpIndex 체크포인트 인덱스
     * @return 사용 중인지 여부
     */
    fun existsByCourse_CourseIdAndCpIndex(courseId: Long, cpIndex: Int): Boolean
    
    /**
     * 특정 코스의 체크포인트를 타입별로 조회합니다
     * 
     * @param courseId 코스 ID
     * @param cpIdPattern 체크포인트 ID 패턴 (예: 'CP%', 'WATER%')
     * @return 패턴에 맞는 체크포인트 목록
     */
    @Query("""
        SELECT c FROM Checkpoint c 
        WHERE c.course.courseId = :courseId 
        AND c.cpId LIKE :cpIdPattern 
        ORDER BY c.cpIndex ASC
    """)
    fun findByCourseIdAndCpIdPattern(
        @Param("courseId") courseId: Long,
        @Param("cpIdPattern") cpIdPattern: String
    ): List<Checkpoint>
    
    /**
     * 특정 코스의 중간 체크포인트만 조회합니다 (시작점과 완주점 제외)
     * 
     * @param courseId 코스 ID
     * @return 중간 체크포인트 목록
     */
    @Query("""
        SELECT c FROM Checkpoint c 
        WHERE c.course.courseId = :courseId 
        AND c.cpIndex > 0 
        AND c.cpId != 'FINISH' 
        ORDER BY c.cpIndex ASC
    """)
    fun findIntermediateCheckpoints(@Param("courseId") courseId: Long): List<Checkpoint>
    
    /**
     * 특정 코스의 두 체크포인트 사이의 거리를 계산합니다
     * 
     * @param courseId 코스 ID
     * @param fromIndex 시작 체크포인트 인덱스
     * @param toIndex 끝 체크포인트 인덱스
     * @return 두 체크포인트 사이의 거리
     */
    @Query("""
        SELECT (to_cp.distanceFromStart - from_cp.distanceFromStart) 
        FROM Checkpoint from_cp, Checkpoint to_cp 
        WHERE from_cp.course.courseId = :courseId 
        AND to_cp.course.courseId = :courseId 
        AND from_cp.cpIndex = :fromIndex 
        AND to_cp.cpIndex = :toIndex
    """)
    fun calculateDistanceBetweenCheckpoints(
        @Param("courseId") courseId: Long,
        @Param("fromIndex") fromIndex: Int,
        @Param("toIndex") toIndex: Int
    ): BigDecimal?
    
    /**
     * 특정 코스의 체크포인트 중에서 특정 고도 이상의 체크포인트를 조회합니다
     * 
     * @param courseId 코스 ID
     * @param minAltitude 최소 고도
     * @return 고도 조건을 만족하는 체크포인트 목록
     */
    fun findByCourse_CourseIdAndAltitudeGreaterThanEqualOrderByCpIndexAsc(
        courseId: Long,
        minAltitude: BigDecimal
    ): List<Checkpoint>
    
    /**
     * 특정 시간 범위에 생성된 체크포인트를 조회합니다
     * 
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @return 시간 범위 내에 생성된 체크포인트 목록
     */
    @Query("""
        SELECT c FROM Checkpoint c 
        WHERE c.createdAt BETWEEN :startTime AND :endTime 
        ORDER BY c.createdAt ASC
    """)
    fun findCreatedBetween(
        @Param("startTime") startTime: LocalDateTime,
        @Param("endTime") endTime: LocalDateTime
    ): List<Checkpoint>
} 
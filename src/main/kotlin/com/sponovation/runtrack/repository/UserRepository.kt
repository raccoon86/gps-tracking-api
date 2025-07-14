package com.sponovation.runtrack.repository

import com.sponovation.runtrack.domain.User
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.*
/**
 * 사용자 데이터 접근을 위한 Repository 인터페이스
 * 
 * 사용자 관련 CRUD 연산과 커스텀 쿼리를 제공합니다.
 * Spring Data JPA를 사용하여 기본적인 데이터베이스 연산을 자동화하고,
 * 사용자 검색, 인증, 프로필 관리를 위한 커스텀 쿼리를 추가 정의합니다.
 */
@Repository
interface UserRepository : JpaRepository<User, Long> {
    
    // === 기본 조회 메서드 ===
    
    /**
     * 이메일로 사용자 조회
     * 
     * @param email 조회할 이메일
     * @return 사용자 정보 (Optional)
     */
    fun findByEmail(email: String): Optional<User>
    
    /**
     * 전화번호로 사용자 조회
     * 
     * @param phoneNumber 조회할 전화번호
     * @return 사용자 정보 (Optional)
     */
    fun findByPhoneNumber(phoneNumber: String): Optional<User>
    
    /**
     * 닉네임으로 사용자 조회
     * 
     * @param nickname 조회할 닉네임
     * @return 사용자 정보 (Optional)
     */
    fun findByNickname(nickname: String): Optional<User>
    
    /**
     * 이메일 존재 여부 확인
     * 
     * @param email 확인할 이메일
     * @return 존재 여부
     */
    fun existsByEmail(email: String): Boolean
    
    /**
     * 전화번호 존재 여부 확인
     * 
     * @param phoneNumber 확인할 전화번호
     * @return 존재 여부
     */
    fun existsByPhoneNumber(phoneNumber: String): Boolean
    
    /**
     * 닉네임 존재 여부 확인
     * 
     * @param nickname 확인할 닉네임
     * @return 존재 여부
     */
    fun existsByNickname(nickname: String): Boolean
    
    // === 상태별 조회 메서드 ===
    
    /**
     * 상태별 사용자 조회
     * 
     * @param status 조회할 상태
     * @param pageable 페이징 정보
     * @return 사용자 페이지
     */
    fun findByStatus(status: String, pageable: Pageable): Page<User>
    
    /**
     * 활성 사용자 조회
     * 
     * @param pageable 페이징 정보
     * @return 활성 사용자 페이지
     */
    @Query("SELECT u FROM User u WHERE u.status = 'ACTIVE'")
    fun findActiveUsers(pageable: Pageable): Page<User>
    
    /**
     * 개인정보 공개 설정별 사용자 조회
     * 
     * @param privacySetting 개인정보 공개 설정
     * @param pageable 페이징 정보
     * @return 사용자 페이지
     */
    fun findByPrivacySetting(privacySetting: String, pageable: Pageable): Page<User>
    
    // === 검색 메서드 ===
    
    /**
     * 이름으로 사용자 검색 (부분 일치)
     * 
     * @param name 검색할 이름
     * @param pageable 페이징 정보
     * @return 사용자 페이지
     */
    fun findByNameContainingIgnoreCase(name: String, pageable: Pageable): Page<User>
    
    /**
     * 닉네임으로 사용자 검색 (부분 일치)
     * 
     * @param nickname 검색할 닉네임
     * @param pageable 페이징 정보
     * @return 사용자 페이지
     */
    fun findByNicknameContainingIgnoreCase(nickname: String, pageable: Pageable): Page<User>
    
    /**
     * 이름 또는 닉네임으로 사용자 검색
     * 
     * @param keyword 검색 키워드
     * @param pageable 페이징 정보
     * @return 사용자 페이지
     */
    @Query("SELECT u FROM User u WHERE LOWER(u.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(u.nickname) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    fun findByNameOrNicknameContainingIgnoreCase(@Param("keyword") keyword: String, pageable: Pageable): Page<User>
    
    // === 시간 기반 조회 메서드 ===
    
    /**
     * 특정 날짜 이후에 생성된 사용자 조회
     * 
     * @param date 기준 날짜
     * @param pageable 페이징 정보
     * @return 사용자 페이지
     */
    fun findByCreatedAtAfter(date: LocalDateTime, pageable: Pageable): Page<User>
    
    /**
     * 특정 날짜 이후에 로그인한 사용자 조회
     * 
     * @param date 기준 날짜
     * @param pageable 페이징 정보
     * @return 사용자 페이지
     */
    fun findByLastLoginAtAfter(date: LocalDateTime, pageable: Pageable): Page<User>
    
    /**
     * 최근 N일 이내에 로그인한 활성 사용자 조회
     * 
     * @param days 기준 일수
     * @return 사용자 목록
     */
    @Query("SELECT u FROM User u WHERE u.status = 'ACTIVE' AND u.lastLoginAt >= :date")
    fun findActiveUsersWithRecentLogin(@Param("date") date: LocalDateTime): List<User>
    
    // === 프로필 관련 조회 메서드 ===
    
    /**
     * 프로필 이미지가 있는 사용자 조회
     * 
     * @param pageable 페이징 정보
     * @return 사용자 페이지
     */
    @Query("SELECT u FROM User u WHERE u.profileImageUrl IS NOT NULL AND u.profileImageUrl != ''")
    fun findUsersWithProfileImage(pageable: Pageable): Page<User>
    
    /**
     * 프로필 이미지가 없는 사용자 조회
     * 
     * @param pageable 페이징 정보
     * @return 사용자 페이지
     */
    @Query("SELECT u FROM User u WHERE u.profileImageUrl IS NULL OR u.profileImageUrl = ''")
    fun findUsersWithoutProfileImage(pageable: Pageable): Page<User>
    
    // === 통계 조회 메서드 ===
    
    /**
     * 상태별 사용자 수 조회
     * 
     * @param status 조회할 상태
     * @return 사용자 수
     */
    fun countByStatus(status: String): Long
    
    /**
     * 전체 활성 사용자 수 조회
     * 
     * @return 활성 사용자 수
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.status = 'ACTIVE'")
    fun countActiveUsers(): Long
    
    /**
     * 특정 날짜 이후 가입한 사용자 수 조회
     * 
     * @param date 기준 날짜
     * @return 신규 사용자 수
     */
    fun countByCreatedAtAfter(date: LocalDateTime): Long
    
    /**
     * 성별별 사용자 수 조회
     * 
     * @param gender 성별
     * @return 사용자 수
     */
    fun countByGender(gender: String): Long
    
    // === 배치 조회 메서드 ===
    
    /**
     * 여러 사용자 ID로 사용자 정보 조회
     * topRankers 구현에서 사용
     * 
     * @param userIds 조회할 사용자 ID 목록
     * @return 사용자 정보 목록
     */
    @Query("SELECT u FROM User u WHERE u.userId IN :userIds")
    fun findByUserIdIn(@Param("userIds") userIds: List<Long>): List<User>
    
    /**
     * 사용자 ID 문자열로 사용자 정보 조회
     * Redis에서 조회한 문자열 ID를 UUID로 변환하여 조회
     * 
     * @param userIdStrings 조회할 사용자 ID 문자열 목록
     * @return 사용자 정보 목록
     */
    @Query("SELECT u FROM User u WHERE CAST(u.userId AS string) IN :userIdStrings")
    fun findByUserIdStrings(@Param("userIdStrings") userIdStrings: List<String>): List<User>
    
    // === 관리자 전용 메서드 ===
    
    /**
     * 비활성화된 사용자 조회 (관리자용)
     * 
     * @param pageable 페이징 정보
     * @return 비활성화된 사용자 페이지
     */
    @Query("SELECT u FROM User u WHERE u.status IN ('INACTIVE', 'SUSPENDED', 'DELETED')")
    fun findInactiveUsers(pageable: Pageable): Page<User>
    
    /**
     * 특정 기간 동안 로그인하지 않은 사용자 조회 (휴면 계정 관리)
     * 
     * @param date 기준 날짜 (이 날짜 이전에 마지막 로그인)
     * @param pageable 페이징 정보
     * @return 휴면 계정 후보 사용자 페이지
     */
    @Query("SELECT u FROM User u WHERE u.status = 'ACTIVE' AND (u.lastLoginAt IS NULL OR u.lastLoginAt < :date)")
    fun findDormantAccountCandidates(@Param("date") date: LocalDateTime, pageable: Pageable): Page<User>
    
    // === 유틸리티 메서드 ===
    
    /**
     * 사용자 이름으로 기본 프로필 정보 조회
     * 
     * @param name 사용자 이름
     * @return 기본 프로필 정보 (이름, 닉네임, 프로필 이미지 URL)
     */
    @Query("SELECT new map(u.name as name, u.nickname as nickname, u.profileImageUrl as profileImageUrl) FROM User u WHERE u.name = :name")
    fun findBasicProfileByName(@Param("name") name: String): Map<String, Any>?
    
    /**
     * 사용자 ID로 기본 프로필 정보 조회
     * 
     * @param userId 사용자 ID
     * @return 기본 프로필 정보 (이름, 닉네임, 프로필 이미지 URL)
     */
    @Query("SELECT new map(u.name as name, u.nickname as nickname, u.profileImageUrl as profileImageUrl) FROM User u WHERE u.userId = :userId")
    fun findBasicProfileByUserId(@Param("userId") userId: Long): Map<String, Any>?
} 
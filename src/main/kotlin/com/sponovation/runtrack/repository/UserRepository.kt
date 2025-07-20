package com.sponovation.runtrack.repository

import com.sponovation.runtrack.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
/**
 * 사용자 데이터 접근을 위한 Repository 인터페이스
 * 
 * 사용자 관련 CRUD 연산과 커스텀 쿼리를 제공합니다.
 * Spring Data JPA를 사용하여 기본적인 데이터베이스 연산을 자동화하고,
 * 사용자 검색, 인증, 프로필 관리를 위한 커스텀 쿼리를 추가 정의합니다.
 */
@Repository
interface UserRepository : JpaRepository<User, Long>
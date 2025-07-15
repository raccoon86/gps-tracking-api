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
interface CheckpointRepository : JpaRepository<Checkpoint, Long>
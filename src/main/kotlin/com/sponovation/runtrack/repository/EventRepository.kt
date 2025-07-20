package com.sponovation.runtrack.repository

import com.sponovation.runtrack.domain.Event
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * 이벤트 데이터 접근을 위한 Repository 인터페이스
 * 
 * 이벤트 관련 CRUD 연산과 커스텀 쿼리를 제공합니다.
 * Spring Data JPA를 사용하여 기본적인 데이터베이스 연산을 자동화하고,
 * 복잡한 비즈니스 로직을 위한 커스텀 쿼리를 추가 정의합니다.
 */
@Repository
interface EventRepository : JpaRepository<Event, Long>
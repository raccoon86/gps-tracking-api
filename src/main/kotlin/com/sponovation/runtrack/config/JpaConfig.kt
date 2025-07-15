package com.sponovation.runtrack.config

import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

/**
 * JPA 설정 클래스
 * 
 * JPA Auditing 기능을 활성화하여 
 * @CreatedDate, @LastModifiedDate 어노테이션이 자동으로 동작하도록 합니다.
 */
@Configuration
@EnableJpaAuditing
class JpaConfig 
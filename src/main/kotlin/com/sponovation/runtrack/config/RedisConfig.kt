package com.sponovation.runtrack.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer

/**
 * Redis 캐시 시스템 설정 클래스
 * 
 * 이 설정 클래스는 다음과 같은 Redis 관련 기능을 구성합니다:
 * 
 * 주요 기능:
 * - Redis 연결 관리 (Lettuce 클라이언트 사용)
 * - JSON 직렬화/역직렬화 설정 (Jackson 기반)
 * - Kotlin 데이터 클래스 지원
 * - LocalDateTime 등 Java 8 시간 API 지원
 * 
 * 사용 목적:
 * - 실시간 위치 데이터 캐싱
 * - 코스 데이터 임시 저장
 * - 세션 정보 고속 조회
 * - 순위 정보 실시간 업데이트
 * 
 * 성능 최적화:
 * - 연결 풀 관리를 통한 효율적인 커넥션 사용
 * - JSON 형태의 구조화된 데이터 저장
 * - 타입 안전성을 보장하는 직렬화 설정
 * 
 * @see LettuceConnectionFactory Redis 연결 팩토리
 * @see RedisTemplate Redis 조작 템플릿
 * @see ObjectMapper JSON 직렬화 매퍼
 */
@Configuration
class RedisConfig {

    /** 
     * Redis 서버 호스트 주소
     * application.yml에서 설정, 기본값은 localhost
     */
    @Value("\${spring.redis.host:localhost}")
    private lateinit var host: String

    /** 
     * Redis 서버 포트 번호
     * application.yml에서 설정, 기본값은 6379
     */
    @Value("\${spring.redis.port:6379}")
    private var port: Int = 6379

    /**
     * Redis 연결 팩토리 빈 생성
     * 
     * Lettuce 클라이언트를 사용하여 Redis 서버와의 연결을 관리합니다.
     * Lettuce는 비동기 및 리액티브 프로그래밍을 지원하는 고성능 Redis 클라이언트입니다.
     * 
     * 장점:
     * - 연결 풀링을 통한 효율적인 리소스 관리
     * - 네트워크 I/O의 비동기 처리
     * - 클러스터 환경 지원
     * 
     * @return Redis 연결 팩토리 인스턴스
     */
    @Bean
    fun redisConnectionFactory(): RedisConnectionFactory {
        return LettuceConnectionFactory(host, port)
    }

    /**
     * Redis 전용 JSON 직렬화 ObjectMapper 빈 생성
     * 
     * 이 ObjectMapper는 Redis에 저장될 데이터의 JSON 변환을 담당합니다.
     * 다음과 같은 특별한 설정이 적용됩니다:
     * 
     * 모듈 등록:
     * - JavaTimeModule: LocalDateTime, Instant 등 Java 8 시간 API 지원
     * - KotlinModule: Kotlin 데이터 클래스 직렬화 지원
     * 
     * 직렬화 설정:
     * - 날짜/시간을 ISO-8601 문자열 형태로 저장 (타임스탬프 대신)
     * - 알 수 없는 프로퍼티 무시 (하위 호환성 보장)
     * 
     * @return Redis 전용 ObjectMapper 인스턴스
     */
    @Bean
    fun redisObjectMapper(): ObjectMapper {
        return ObjectMapper().apply {
            // Java 8 시간 API 지원 모듈 등록
            registerModule(JavaTimeModule())
            // Kotlin 언어 지원 모듈 등록
            registerKotlinModule()
            // 날짜를 문자열로 직렬화 (WRITE_DATES_AS_TIMESTAMPS 비활성화)
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            // 알 수 없는 프로퍼티가 있어도 역직렬화 실패하지 않음
            configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    }

    /**
     * Redis 조작을 위한 템플릿 빈 생성
     * 
     * RedisTemplate은 Redis 서버와의 모든 상호작용을 담당하는 핵심 컴포넌트입니다.
     * 키-값 저장, 조회, 삭제 등의 기본 연산부터 복잡한 데이터 구조 조작까지 지원합니다.
     * 
     * 직렬화 전략:
     * - Key: String 직렬화 (Redis 표준 키 형태 유지)
     * - Value: JSON 직렬화 (복잡한 객체 구조 저장 가능)
     * - Hash: Key-Value 모두 해당 직렬화 방식 적용
     * 
     * 사용 예시:
     * ```kotlin
     * redisTemplate.opsForValue().set("user:123", userObject)
     * val user = redisTemplate.opsForValue().get("user:123") as User
     * ```
     * 
     * @param connectionFactory Redis 연결 팩토리
     * @param redisObjectMapper JSON 직렬화용 ObjectMapper
     * @return 설정된 RedisTemplate 인스턴스
     */
    @Bean
    fun redisTemplate(connectionFactory: RedisConnectionFactory, redisObjectMapper: ObjectMapper): RedisTemplate<String, Any> {
        val template = RedisTemplate<String, Any>()
        template.connectionFactory = connectionFactory
        
        // Key는 String으로 직렬화 (가독성과 Redis 표준 준수)
        template.keySerializer = StringRedisSerializer()
        template.hashKeySerializer = StringRedisSerializer()
        
        // Value는 JSON으로 직렬화 (타입 정보 없이 순수 JSON 형태)
        val jsonSerializer = Jackson2JsonRedisSerializer(Object::class.java).apply {
            setObjectMapper(redisObjectMapper)
        }
        template.valueSerializer = jsonSerializer
        template.hashValueSerializer = jsonSerializer
        
        // 설정 완료 후 초기화
        template.afterPropertiesSet()
        return template
    }
} 
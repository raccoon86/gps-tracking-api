package com.sponovation.runtrack.config

import com.sponovation.runtrack.common.LoggingInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * 웹 애플리케이션 전역 설정 클래스
 *
 * Spring MVC의 전역 설정을 담당하며, 다음과 같은 기능을 구성합니다:
 *
 * CORS (Cross-Origin Resource Sharing) 설정:
 * - 프론트엔드 애플리케이션과의 통신 허용
 * - 브라우저 보안 정책 우회를 위한 설정
 * - RESTful API 접근 권한 관리
 *
 * 인터셉터 설정:
 * - HTTP 요청/응답 로깅
 * - 보안 검증 및 인증 처리
 * - 성능 모니터링 및 디버깅
 *
 * 보안 고려사항:
 * - 운영 환경에서는 특정 도메인만 허용 권장
 * - 민감한 헤더 정보 노출 방지
 * - 적절한 캐시 정책 설정
 *
 * @see LoggingInterceptor HTTP 요청/응답 로깅 인터셉터
 * @see WebMvcConfigurer Spring MVC 설정 인터페이스
 */
@Configuration
class WebConfig(
    /** HTTP 요청/응답 로깅을 담당하는 인터셉터 */
    private val loggingInterceptor: LoggingInterceptor
) : WebMvcConfigurer {

    /**
     * CORS (Cross-Origin Resource Sharing) 정책 설정
     *
     * 웹 브라우저의 동일 출처 정책(Same-Origin Policy)으로 인해 차단되는
     * 크로스 도메인 요청을 허용하기 위한 설정입니다.
     *
     * 설정 상세:
     * - 허용 출처: localhost:8080 (개발 환경용)
     * - 허용 메서드: GET, POST, PATCH, PUT, DELETE, OPTIONS
     * - 허용 헤더: Authorization(인증), Content-Type(데이터 형식), RefreshToken(갱신)
     * - 자격 증명 허용: true (쿠키, 인증 헤더 포함 요청 가능)
     * - 프리플라이트 캐시: 3600초 (1시간)
     *
     * 보안 참고사항:
     * - 운영 환경에서는 allowedOrigins를 특정 도메인으로 제한 필요
     * - allowCredentials(true) 사용 시 allowedOrigins("*") 사용 불가
     * - 민감한 API 엔드포인트는 추가 보안 검증 필요
     *
     * 프리플라이트 요청:
     * - 브라우저가 실제 요청 전에 OPTIONS 메서드로 사전 확인
     * - 서버의 CORS 정책을 확인 후 실제 요청 수행
     * - maxAge 설정으로 불필요한 프리플라이트 요청 최소화
     *
     * @param registry CORS 설정을 위한 레지스트리
     */
    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/**") // 모든 API 엔드포인트에 CORS 정책 적용
            .allowedOrigins(
                "http://localhost:8080" // 개발 환경: 로컬 프론트엔드 서버
                // TODO: 운영 환경에서는 실제 프론트엔드 도메인으로 변경 필요
                // "https://test.mypb.info", "https://mypb.info"
            )
            .allowedMethods("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS") // 허용할 HTTP 메서드
            .allowedHeaders("Authorization", "Content-Type", "RefreshToken") // 허용할 요청 헤더
            .allowCredentials(true) // 자격 증명 정보 포함 요청 허용 (쿠키, Authorization 헤더 등)
            .maxAge(3600) // 프리플라이트 응답 캐시 시간 (초 단위)
    }

    /**
     * HTTP 요청 인터셉터 등록
     *
     * 모든 HTTP 요청에 대해 공통적으로 실행될 인터셉터를 등록합니다.
     * 인터셉터는 다음과 같은 순서로 처리됩니다:
     *
     * 요청 처리 순서:
     * 1. preHandle(): 컨트롤러 실행 전 처리
     * 2. Controller 메서드 실행
     * 3. postHandle(): 컨트롤러 실행 후, 뷰 렌더링 전 처리
     * 4. afterCompletion(): 요청 완료 후 처리 (예외 발생 시에도 실행)
     *
     * LoggingInterceptor 기능:
     * - 요청 URL, 메서드, 파라미터 로깅
     * - 응답 상태 코드 및 처리 시간 기록
     * - 예외 발생 시 오류 상세 정보 로깅
     * - 디버깅 및 성능 모니터링 지원
     *
    */
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(loggingInterceptor) // 로깅 인터셉터 등록
            .addPathPatterns("/**") // 모든 경로에 적용
            // .excludePathPatterns("/actuator/**") // 필요시 특정 경로 제외 가능
    }
}

package com.sponovation.runtrack.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * CORS(Cross-Origin Resource Sharing) 설정
 * 
 * 프론트엔드에서 백엔드 API에 접근할 수 있도록 CORS 정책을 설정합니다.
 * 로컬 개발 환경에서 HTML 파일을 직접 열어도 API 호출이 가능하도록 구성되어 있습니다.
 */
@Configuration
class CorsConfig : WebMvcConfigurer {

    /**
     * 전역 CORS 설정
     * 
     * 모든 API 엔드포인트에 대해 CORS를 허용합니다.
     */
    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/api/**")
            .allowedOriginPatterns("*") // 모든 origin 허용 (개발용)
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)
            .maxAge(3600) // 1시간 동안 preflight 결과 캐시
    }

    /**
     * 상세 CORS 설정
     * 
     * Spring Security와 함께 사용할 때를 위한 Bean 설정입니다.
     */
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            // 로컬 개발환경에서 사용할 origin들 허용
            allowedOriginPatterns = listOf(
                "http://localhost:*",
                "http://127.0.0.1:*", 
                "file://*",  // 로컬 HTML 파일
                "*"  // 개발용 - 운영환경에서는 제거 필요
            )
            
            allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = true
            maxAge = 3600L
        }

        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/api/**", configuration)
        }
    }
} 
# RunTrack API

GPS 트래킹과 경로 매칭을 위한 Spring Boot API 서버입니다.

## 목차

- [개요](#개요)
- [사전 요구사항](#사전-요구사항)
- [설치 및 설정](#설치-및-설정)
- [실행 방법](#실행-방법)
- [테스트](#테스트)
- [주요 기능](#주요-기능)
- [API 문서](#api-문서)
- [데이터베이스](#데이터베이스)
- [환경 설정](#환경-설정)
- [API 엔드포인트](#api-엔드포인트)
- [알고리즘 상세](#알고리즘-상세)
- [기술 스택](#기술-스택)
- [트러블슈팅](#트러블슈팅)
- [라이센스](#라이센스)

## 개요

RunTrack API는 마라톤 및 러닝 애플리케이션을 위한 백엔드 서비스로, GPS 데이터 수집, 경로 매칭, 실시간 트래킹 기능을 제공합니다.

## 사전 요구사항

- **Java**: JDK 17 이상
- **Gradle**: 8.0 이상 (Gradle Wrapper 포함)
- **Git**: 최신 버전

### 선택사항

- **MySQL**: 8.0 이상 (운영 환경)
- **Redis**: 6.0 이상 (캐싱, 선택사항)

## 설치 및 설정

### 1. 저장소 클론

```bash
git clone https://github.com/sponovation/my-pb-venue-api.git
```

### 2. 권한 설정 (macOS/Linux)

```bash
chmod +x gradlew
```

### 3. 의존성 설치

```bash
./gradlew build
```

### 4. 환경 설정 파일 구성

#### 개발 환경 (기본)

`src/main/resources/application.yml` 파일이 기본 설정으로 사용됩니다.

#### 운영 환경

환경변수를 설정하거나 `application-prod.yml` 파일을 생성:

```bash
# 환경변수 설정
export REDIS_HOST=localhost
export REDIS_PORT=6379
```

## 실행 방법

### 개발 환경 실행

```bash
# Gradle Wrapper 사용
./gradlew bootRun

# 또는 Gradle이 설치된 경우
gradle bootRun
```

### JAR 파일로 실행

```bash
# JAR 파일 빌드
./gradlew bootJar

# JAR 파일 실행
java -jar build/libs/runtrack-0.0.1-SNAPSHOT.jar
```

### 프로필별 실행

```bash
# 개발 프로필
./gradlew bootRun --args='--spring.profiles.active=dev'

# 운영 프로필
./gradlew bootRun --args='--spring.profiles.active=prod'
```

### 애플리케이션 확인

서버가 정상적으로 시작되면 다음 URL에서 확인 가능합니다:

- **애플리케이션**: http://localhost:8080
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **헬스체크**: http://localhost:8080/actuator/health

## 주요 기능

### 1. GPS 데이터 수집 및 처리

- 실시간 GPS 데이터 수집 및 저장
- Kalman 필터를 통한 GPS 신호 노이즈 제거
- GPS 정확도 검증 및 품질 관리

### 2. GPX 경로 파싱 및 관리

- GPX 파일 업로드 및 파싱
- 경로 데이터 추출 및 저장
- 자동 체크포인트 생성 (1km 간격)

### 3. 맵 매칭 엔진

- GPS 좌표를 GPX 경로에 매칭
- Weighted Snap-to-Road 알고리즘 적용
- 산악 환경에서의 안정적인 경로 매칭

### 4. 오차 보정 시스템

- Kalman 필터를 통한 GPS 신호 보정
- 방위각 기반 경로 매칭 점수 계산
- 실시간 경로 이탈 감지

### 5. 체크포인트 및 알림

- 체크포인트 도달 감지
- 경로 이탈 시 알림 전송
- 트래킹 완료 통계 제공

## API 문서

애플리케이션 실행 후 다음 URL에서 API 문서를 확인할 수 있습니다:

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/api-docs

## 환경 설정

### 환경별 설정 파일

- `application.yml`: 기본 설정
- `application-dev.yml`: 개발 환경
- `application-prod.yml`: 운영 환경
- `application-test.yml`: 테스트 환경

## 알고리즘 상세

### Kalman 필터

- GPS 위치 데이터의 노이즈 제거
- 속도 벡터를 포함한 4차원 상태 벡터 [x, y, vx, vy]
- 측정 정확도에 따른 적응적 필터링

### 맵 매칭

- 거리 기반 가중치 (60%)
- 방위각 기반 가중치 (40%)
- 경로에서 100m 이상 벗어날 경우 이탈로 판단

### 체크포인트 시스템

- GPX 경로 기반 자동 체크포인트 생성
- 1km 간격으로 체크포인트 배치
- 50m 반경 내 진입 시 도달로 인정

## 기술 스택

- **Framework**: Spring Boot 3.5.0
- **Language**: Kotlin 1.9.25
- **Database**: H2 (개발) / MySQL (운영)
- **ORM**: Spring Data JPA + Hibernate
- **Documentation**: SpringDoc OpenAPI (Swagger)
- **Build Tool**: Gradle
- **Libraries**:
  - JPX (GPX 파싱)
  - Apache Commons Math (Kalman 필터)
  - Firebase Admin SDK (푸시 알림)

## 트러블슈팅

### 일반적인 문제

#### 1. 빌드 실패

```bash
# Gradle 캐시 정리
./gradlew clean build

# Gradle Wrapper 다시 다운로드
./gradlew wrapper --gradle-version 8.5
```

#### 2. 포트 충돌 (8080)

```bash
# 다른 포트로 실행
./gradlew bootRun --args='--server.port=8081'
```

#### 3. 메모리 부족

```bash
# JVM 옵션 설정
export JAVA_OPTS="-Xmx2g -Xms1g"
./gradlew bootRun
```

### 로그 확인

```bash
# 애플리케이션 로그
tail -f logs/runtrack.log

# 특정 레벨 로그만 보기
grep "ERROR" logs/runtrack.log
```

## 라이센스

MIT License

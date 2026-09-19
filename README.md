# Rezy
Restaurant reservation platform with concurrency control and performance tuning

## 🛠️ 기술 스택

| Backend | Database | Infra |
|---------|----------|-------|
| Java 17 / Spring Boot 3.4.5 | MariaDB / Redis | AWS EC2 / Docker / k6 |

## 🗂️ ERD
<img width="1481" height="735" alt="Rezy-2026-07-22T00_21_49" src="https://github.com/user-attachments/assets/ceec94ee-9950-4949-a3c7-76d3fcf627a4" />


## 인증/인가 (JWT + 소셜 로그인)
### 1. 소셜 로그인 사용자 식별 : 이메일이 아닌 (provider, provider_id) 사용
카카오는 이메일이 선택 동의 항목이라 PK로 사용이 불가능.
이메일을 유저 식별로 사용하면 재로그인 시 사용자를 찾지 못하는 문제가 생겨,
'User' 엔티티에 소셜 플랫폼이 발급하는 고유 식별자 (`providerId`)를 별도 저장하고
`(provider, provider_id)` 복합 유니크 제약으로 계정 식별하도록 설계

### 2. Strategy 패턴 (OAuth2UserInfo 추상화) : 소셜 플랫폼 응답 구조 통합
카카오와 네이버는 사용자 정보 응답 JSON 구조가 다르기 때문에 'OAuth2UserInfo' 인터페이스로 추상화 하고
`KakaoUserInfo `/`NaverUserInfo` 구현체 + 팩토리로 나눠서, 신규 플랫폼 추가 시
`CustomOAuth2UserService` 등 기존 로직 수정 없이 구현체 하나만 추가하면 되도록 설계

## 예약 도메인 설계
### 1. 예약 슬롯 : 인원별 정원 분리 (1:N)
한 시간대에 "2인 이하 3팀, 4인 이하 2팀"처럼 인원별로 정원이 나뉘기 때문에 단일 컬럼으로 표현 불가
`ReservationSlot` -> `SlotCapacity`(인원 버킷 별 잔여 정원) **1:N 구조로 분리**
인원 튜플 마다 `remaining_teams`를 두어 예약 시 해당 튜플만 차감하도록 설계

## 성능 개선

측정 환경: EC2 2대 분리 (앱+DB+Redis / k6 부하 생성기), 같은 VPC
🔗 [상세 측정 과정](노션 링크)

### 1. 동시 예약 정합성 — 락 방식 4가지 비교
정원 3팀인 슬롯에 200VU 동시 요청 시 32건 생성 (Race Condition)
락 없음 / synchronized / 비관적락 / 원자적 UPDATE 를 집중형·분산형으로 측정
→ 확장성을 기준으로 비관적 락 채택

### 2. Redis 원자적 차감으로 락 경합 제거
비관적 락은 슬롯 하나당 한 건씩만 처리되어 처리량이 슬롯 수에 묶임
Redis DECR로 재고 차감을 DB 밖으로 이동

|        | 비관적 락 | Redis |
|--------|----------|-------|
| 분산형 (슬롯 10개) | 941 TPS | 942 TPS |
| 집중형 (슬롯 1개)  | 774 TPS | 955 TPS |
| 변화   | -18% | +1.4% |

### 3. 슬롯 조회 N+1 제거
시간대 수에 비례해 쿼리 증가 (22개 조회 시 24개 쿼리)
→ fetch join 적용, 쿼리 2개로 고정

### 4. 복합 인덱스로 조회 범위 축소
(store_id, slot_datetime) 복합 인덱스 추가
→ 스캔 행 660 → 22

## CI/CD
main 브랜치 push 시 GitHub Actions가 EC2에 접속해
`git pull` → Docker 이미지 재빌드 → 컨테이너 교체까지 자동 수행
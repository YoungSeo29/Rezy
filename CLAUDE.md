# Rezy

레스토랑 예약 플랫폼. 백엔드 포트폴리오 프로젝트. 개발자 1인(본인).

## 스택
- Spring Boot 3.4.5 / Java 17 / Gradle
- MariaDB 11.5 / Redis 7.4
- Docker Compose / AWS EC2 / GitHub Actions

## 환경변수
`.env` + spring-dotenv 로 주입한다.
`application-local.yml` 같은 프로파일 yml을 만들지 마라.

Spring Boot 4.x 로 올리지 마라. spring-dotenv가 동작하지 않아
`Driver claims to not accept jdbcUrl, ${DB_URL}` 로 터진다.

## 도메인 설계 규칙
- 모든 PK는 VARCHAR(36) UUID. `@PrePersist`에서 생성.
- 엔티티에 setter 금지. 정적 팩토리(`create...`)와 의미 있는 메서드만 둔다.
- 연관관계는 `FetchType.LAZY` 고정.
- 예약 하루 1건 제한은 `(user_id, active_date)` 유니크 제약으로 구현.
  취소 시 `active_date`를 NULL로 만든다 (MariaDB는 NULL 중복을 허용).

## 재고 관리 (중요)
예약 재고의 실시간 값은 **Redis**다. 키는 `slot:cap:{slotCapacityId}`.

- 예약: Redis `DECR` → 음수면 `INCR`로 되돌리고 예외
- DB의 `remaining_teams`는 예약 시 갱신하지 않는다. X-lock 회피를 위한 의도된 설계.
  "DB가 안 맞는다"며 갱신 로직을 추가하지 마라.
- 조회 응답은 Redis 값 우선, 키가 없으면 DB 값으로 폴백.
- 롤백 보상은 `afterCompletion(STATUS_ROLLED_BACK)`.
  `afterCommit`은 커밋 실패를 못 잡으므로 쓰지 마라.

## 배포
EC2에서:
docker compose -f docker-compose.prod.yml up -d --build

`-f` 를 빼면 로컬 dev용 `docker-compose.yml`(redis만 있음)이 떠서
app과 mariadb가 올라오지 않는다. 반드시 붙여라.

## 테스트
- 통합 테스트는 `IntegrationTestSupport` 상속. Testcontainers(MariaDB + Redis).
- 동시성 테스트는 `ExecutorService` + `CountDownLatch`.
- 테스트 후 정리는 부모의 `@AfterEach cleanUp()`이 처리한다. 따로 만들지 마라.

## 성능 관련 결정 사항
- 슬롯 조회는 fetch join + distinct 로 N+1을 해결했다. 되돌리지 마라.
- `(store_id, slot_datetime)` 복합 인덱스가 있다.
- 슬롯 조회에 Redis 캐시를 붙이지 마라. 이미 13ms로 캐싱할 문제가 없다.
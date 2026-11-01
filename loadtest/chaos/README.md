# 장애 주입 테스트 (chaos)

Redis / 앱이 죽었을 때 재고 정합성이 깨지는지 확인한다. 성능이 아니라 정합성이 목적.

## 사전 준비

```bash
docker compose -f docker/local/docker-compose.yml up -d              # Redis
docker compose -f docker/local/docker-compose.monitoring.yml up -d   # Prometheus + Grafana
java -jar build/libs/Rezy-0.0.1-SNAPSHOT.jar                         # 앱 (MariaDB 는 로컬 네이티브)
node signup.js 400 ./tokens-chaos.json                               # 토큰 발급 (1시간마다 만료)
```

테스트 전용 행은 슬롯 `chaos-slot-01` / 버킷 `chaos-cap-01` / 사용자 `chaos-0001~0400`.
전부 `seed-store-0001` 가게에 붙는다 (이 가게는 미리 있어야 한다).

**행은 `reset.sh` 가 만든다.** 각 시나리오가 알아서 부르므로 따로 실행할 필요는 없다.
없으면 만들고 있으면 날짜·정원을 다시 맞추므로, DB 를 새로 말아도 그대로 돌아간다.
슬롯은 **오늘 19:00**(19시가 지났으면 내일)로 잡힌다 — 과거 날짜면 배치 검사 범위
(오늘~7일) 밖이라 아무 일도 안 일어난 채로 조용히 끝나기 때문이다.

## 실행

| 시나리오 | 명령 | 확인하는 것 |
|---|---|---|
| A. Redis 재시작 | `bash scenario-a2.sh` | 키 소실 후 재고 복구, 조회 응답 |
| B. 앱 강제 종료 | `bash scenario-b5.sh <앱PID>` → `bash observe-batch-60s.sh` | 재고 누수, 배치 보정 |
| C. Redis 지속 다운 | `bash scenario-c-high.sh` | **커넥션/스레드 고갈이 다른 API 로 번지는지**, 조회·예약·로그인 응답 |

판정은 `bash verify.sh` — `reservations` 의 CONFIRMED 행 수가 기준이다.

## 주의

- **토큰 만료 1시간.** 만료된 토큰으로 돌리면 전부 401 이 나는데 `other_4xx` 로만 잡혀서
  "누수 0" 으로 오독하기 쉽다. 측정 전 반드시 재발급할 것.
- **B 는 DB 를 멈춰야 재현된다.** 그냥 죽이면 요청들이 DECR 이전 단계에서 커넥션을 기다리고
  있어서 누수가 안 생긴다. `scenario-b5.sh` 는 부모 행에 X-lock 을 걸어 INSERT 만 막는다.
- **C 는 평상시 여유와 장애 시 포화가 둘 다 보여야 한다.** `scenario-c-high.sh` 는
  80 VU + think time 0.5s 로 돈다. 평상시엔 커넥션을 1~3 개만 쓰고, Redis 가 죽으면
  전원이 매달려 풀(50)을 채운다. VU 를 300 으로 올리면 평상시부터 포화라 차이가 안 보인다.
- **다운 시간은 벽시계로 고정한다.** 예전 스크립트는 `sleep 30` 뒤에 프로브를 돌리고 Redis 를
  켜서, 프로브가 매달린 만큼 다운 시간이 같이 늘어났다 (수정 전 약 60초 / 수정 후 35초).
  고치면 다운 시간이 저절로 줄어 before-after 비교가 불공정해진다. 지금은 `DOWN` 초를 지킨다.
- **파일로 남는 산출물은 없다.** k6 로그와 앱 로그는 임시파일에 받아 종료 시 지우고,
  결과는 전부 터미널에 찍는다. `tokens-chaos.json` 만 재발급되는 산출물이라 커밋하지 않는다.

## 그래프

Grafana 대시보드 `rezy-chaos` (`docker/local/monitoring/dashboard-chaos.json`).
장애 "중"에는 http 지표가 안 잡힌다 (끝난 요청만 집계되므로).
그 구간의 증거는 커넥션 풀 / 톰캣 스레드 그래프다.

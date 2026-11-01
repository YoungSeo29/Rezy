#!/bin/bash
# 고동시성 C : Redis 지속 다운이 Redis 를 안 쓰는 API 까지 번지는지 확인
# 다운 시간은 프로브 지연과 무관하게 벽시계로 정확히 DOWN 초를 유지한다
NEW="$(cd "$(dirname "$0")" && pwd)"
DOWN="${DOWN:-60}"               # Redis 다운 유지 시간(초)
K6LOG=$(mktemp); trap 'rm -f "$K6LOG"' EXIT
# 슬롯을 오늘(19시 이후면 내일)로 만들고 예약/키를 비운다. 날짜는 reset.sh 가 알려준다.
RESET_DATE=$(bash "$NEW/reset.sh" 400)
DATE="${DATE:-$RESET_DATE}"
PROM="http://127.0.0.1:9090/api/v1/query"
TOK=$(sed 's/^\["//; s/".*$//' "$NEW/tokens-chaos.json")
# 예약 프로브용 - 아직 예약 안 한 토큰을 뒤에서부터 꺼내 쓴다
RTOK=$(node -e "const t=require(process.argv[1]); console.log(t[t.length - Number(process.argv[2])])" "$NEW/tokens-chaos.json" 1)
RIDX=1

m() { curl -s --max-time 5 --get "$PROM" --data-urlencode "query=$1" 2>/dev/null \
      | grep -o '"value":\[[^]]*\]' | head -1 | sed 's/.*,"//; s/"\]//'; }

snap() {
  echo "  [$1] 커넥션 active=$(m 'hikaricp_connections_active{application="rezy"}') pending=$(m 'hikaricp_connections_pending{application="rezy"}')" \
       "| 톰캣 busy=$(m 'tomcat_threads_busy_threads{application="rezy"}')/$(m 'tomcat_threads_config_max_threads{application="rezy"}')"
}

probe() {
  echo "    조회  (Redis 사용)   : $(curl -s -o /dev/null -w 'HTTP=%{http_code}  %{time_total}s' --max-time 25 \
        -H "Authorization: Bearer $TOK" "http://127.0.0.1:8080/api/stores/seed-store-0001/slots?date=$DATE")"
  RIDX=$((RIDX + 1))
  RTOK=$(node -e "const t=require(process.argv[1]); console.log(t[t.length - Number(process.argv[2])])" "$NEW/tokens-chaos.json" $RIDX)
  echo "    예약  (Redis 사용)   : $(curl -s -o /dev/null -w 'HTTP=%{http_code}  %{time_total}s' --max-time 25 \
        -H 'Content-Type: application/json' -H "Authorization: Bearer $RTOK" \
        -X POST -d '{"slotCapacityId":"chaos-cap-01"}' http://127.0.0.1:8080/api/reservations)"
  echo "    로그인(Redis 미사용) : $(curl -s -o /dev/null -w 'HTTP=%{http_code}  %{time_total}s' --max-time 25 \
        -H 'Content-Type: application/json' \
        -d '{"email":"chaos-0002@test.com","password":"Passw0rd!23"}' \
        http://127.0.0.1:8080/api/auth/login)"
}

# T0 기준 target 초가 될 때까지 대기 (이미 지났으면 즉시 통과)
T0=0
hold() { while [ $(( $(date +%s) - T0 )) -lt "$1" ]; do sleep 1; done; }

echo "===== 고동시성 C : Redis 지속 다운 (${DOWN}초) ====="
echo "부하: 슬롯조회 80 VU + 로그인 10/s   대상 날짜: $DATE"
docker exec rezy-redis redis-cli set slot:cap:chaos-cap-01 400 >/dev/null 2>&1
snap "시작전"

cd "$NEW"
k6 run -e TOKENS=./tokens-chaos.json -e DATE="$DATE" -e VUS=80 -e DURATION=170s \
  --quiet c-high.js > "$K6LOG" 2>&1 &
K6=$!

sleep 25
snap "정상부하"
echo "  --- 정상 상태 프로브 ---"; probe

echo
echo "[chaos] $(date +%T)  docker stop rezy-redis   (${DOWN}초 유지)"
docker stop rezy-redis >/dev/null 2>&1
T0=$(date +%s)

for t in 10 20; do hold $t; snap "다운+${t}s"; done
hold 30; echo "  --- 다운 +30s 프로브 ---"; probe
for t in 40 50; do hold $t; snap "다운+${t}s"; done
hold 55; echo "  --- 다운 +55s 프로브 ---"; probe
hold "$DOWN"; snap "다운+${DOWN}s"

echo
echo "[chaos] $(date +%T)  docker start rezy-redis   (다운 실측 $(( $(date +%s) - T0 ))초)"
docker start rezy-redis >/dev/null 2>&1
T0=$(date +%s)

hold 20; snap "복구+20s"; echo "  --- 복구 +20s 프로브 ---"; probe
hold 45; snap "복구+45s"; echo "  --- 복구 +45s 프로브 ---"; probe

wait $K6 2>/dev/null
echo "[k6] 종료"
echo
echo "--- k6 결과 ---"
grep -E "query_|login_|http_req" "$K6LOG"

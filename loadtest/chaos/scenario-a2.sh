#!/bin/bash
# A : 재고 소진 전 + 고동시성 구간에서 Redis 재시작
#     키가 날아가도 재고가 CONFIRMED 기준으로 복구되는지 본다.
S="$(cd "$(dirname "$0")" && pwd)"
K6LOG=$(mktemp); trap 'rm -f "$K6LOG"' EXIT

echo "===== 시나리오 A : 고동시성 구간에서 Redis 재시작 ====="
bash "$S/reset.sh" 400 >/dev/null
echo "--- 시작 전 ---"; bash "$S/verify.sh"; echo

# Redis 값을 계속 읽되 "바뀔 때만" 찍는다. 재시작 전후 전이 지점만 남아서 파일이 필요 없다.
( LAST=""; END=$(( $(date +%s) + 16 ))
  while [ "$(date +%s)" -lt "$END" ]; do
    V=$(docker exec rezy-redis redis-cli get slot:cap:chaos-cap-01 2>/dev/null)
    [ -z "$V" ] && V="(키없음)"
    if [ "$V" != "$LAST" ]; then
      printf "    %s  redis=%s\n" "$(date +%T.%N | cut -c1-11)" "$V"; LAST="$V"
    fi
  done ) &
SAMP=$!

cd "$S"
k6 run -e TOKENS=./tokens-chaos.json -e CAP=chaos-cap-01 -e RATE=200 -e DURATION=2s -e VUS=200 \
   --quiet reserve.js > "$K6LOG" 2>&1 &
K6=$!

sleep 0.6
echo "[chaos] $(date +%T.%N | cut -c1-12)  docker restart rezy-redis"
docker restart rezy-redis
echo "[chaos] $(date +%T.%N | cut -c1-12)  완료"

wait $K6; echo "[k6] 종료"
sleep 4
kill $SAMP 2>/dev/null; wait $SAMP 2>/dev/null

echo; echo "--- k6 결과 ---"; cat "$K6LOG"
echo; echo "--- 최종 검증 ---"; bash "$S/verify.sh"

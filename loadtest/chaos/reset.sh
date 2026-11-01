#!/bin/bash
# chaos 테스트용 행을 "만들고" 초기화한다.
# 없으면 생성하므로 DB 를 새로 말아도 그냥 돌아간다. (예전엔 행이 이미 있다고 가정해서 깨졌다)
#
#   사용법 : D=$(bash reset.sh [정원])    정원 기본 400
#   stdout 은 측정 대상 날짜 한 줄, 진행 로그는 stderr.
MDB="/c/Program Files/MariaDB 11.5/bin/mariadb.exe"
TOTAL=${1:-400}

# 슬롯이 과거면 배치 검사 범위(오늘~7일) 밖이라 아무 일도 안 일어난 채로 끝난다.
# 19시 전이면 오늘, 지났으면 내일.
if [ "$(date +%H%M)" -lt 1900 ]; then D=$(date +%F); else D=$(date -d '+1 day' +%F); fi

"$MDB" -uroot -p'!123456' -h 127.0.0.1 rezy -e "
DELETE FROM reservations WHERE slot_capacity_id='chaos-cap-01';
INSERT INTO reservation_slots (slot_id, store_id, slot_datetime)
VALUES ('chaos-slot-01','seed-store-0001','$D 19:00:00')
ON DUPLICATE KEY UPDATE slot_datetime=VALUES(slot_datetime);
INSERT INTO slot_capacities (slot_capacity_id, slot_id, party_size, total_teams, remaining_teams)
VALUES ('chaos-cap-01','chaos-slot-01',2,$TOTAL,$TOTAL)
ON DUPLICATE KEY UPDATE total_teams=VALUES(total_teams), remaining_teams=VALUES(remaining_teams);" 2>/dev/null

docker exec rezy-redis redis-cli del slot:cap:chaos-cap-01 >/dev/null 2>&1

echo "[reset] 슬롯 $D 19:00 / 정원 $TOTAL / 예약 삭제 / Redis 키 삭제" >&2
echo "$D"

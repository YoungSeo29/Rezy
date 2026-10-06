-- 현재 값이 내가 읽은 값과 같을 때만 교체한다 (Compare And Set)
-- 배치가 GET 한 뒤 SET 하기 전에 사용자의 DECR 이 끼어들면
-- 값이 달라져 있으므로 교체하지 않고 0 을 돌려준다
-- KEYS[1] = 재고 키 / ARGV[1] = 내가 읽었던 값 / ARGV[2] = 새로 쓸 값
if redis.call('GET', KEYS[1]) == ARGV[1] then
    redis.call('SET', KEYS[1], ARGV[2])
    return 1
else
    return 0
end
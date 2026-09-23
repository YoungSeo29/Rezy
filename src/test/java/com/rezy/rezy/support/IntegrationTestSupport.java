package com.rezy.rezy.support;

import com.rezy.rezy.reservation.domain.ReservationSlot;
import com.rezy.rezy.reservation.dto.ReservationCreateRequest;
import com.rezy.rezy.reservation.repository.ReservationRepository;
import com.rezy.rezy.reservation.repository.ReservationSlotRepository;
import com.rezy.rezy.reservation.repository.SlotCapacityRepository;
import com.rezy.rezy.store.StoreRepository;
import com.rezy.rezy.store.domain.Store;
import com.rezy.rezy.user.UserRepository;
import com.rezy.rezy.user.domain.User;
import com.rezy.rezy.user.domain.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MariaDBContainer;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MariaDBContainer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

// 통합 테스트
// 실제 MariaDB, Redis를 Docker 컨테이너로 띄움
// 컨테이너는 static 블록에서 딱 1번만 띄우고 모든 test class가 공유
@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegrationTestSupport {

    // @ServiceConnection : 컨테이너의 주소·포트·계정을 스프링 설정에 자동으로 꽂아준다
    @ServiceConnection
    static final MariaDBContainer<?> MARIADB = new MariaDBContainer<>("mariadb:11.5");

    // Redis 는 전용 컨테이너 클래스가 없어서 GenericContainer + name = "redis" 로 지정
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    static {
        MARIADB.start();
        REDIS.start();
    }

    @Autowired protected UserRepository userRepository;
    @Autowired protected StoreRepository storeRepository;
    @Autowired protected ReservationSlotRepository reservationSlotRepository;
    @Autowired protected SlotCapacityRepository slotCapacityRepository;
    @Autowired protected ReservationRepository reservationRepository;
    @Autowired protected StringRedisTemplate redisTemplate;

    // 테스트끼리 데이터가 섞이지 않도록 매번 전부 비운다 (FK 순서대로)
    @AfterEach
    void cleanUp() {
        reservationRepository.deleteAllInBatch();
        slotCapacityRepository.deleteAllInBatch();
        reservationSlotRepository.deleteAllInBatch();
        storeRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
            connection.serverCommands().flushAll();
        }
    }

    // ---------- 테스트 데이터 헬퍼 ----------

    protected User saveUser(String email) {
        return userRepository.save(User.createLocal(email, "pw"));
    }

    // 서로 다른 유저 n 명 ("하루 1건" 규칙에 안 걸리게 하려고)
    protected List<User> saveUsers(int count) {
        List<User> users = IntStream.rangeClosed(1, count)
                .mapToObj(i -> User.createLocal("user" + i + "@test.com", "pw"))
                .toList();
        return userRepository.saveAll(users);
    }

    // 사장님 + 가게
    protected Store saveStore() {
        User manager = User.createLocal("manager@test.com", "pw");
        manager.completeProfile("사장님", UserRole.MANAGER);
        userRepository.save(manager);
        return storeRepository.save(Store.create("테스트식당", "서울", "11:00-21:00", manager));
    }

    // 슬롯 1개 + 2인 버킷 1개를 만들고, 그 버킷 ID 를 돌려준다
    protected String saveSlot(Store store, LocalDateTime datetime, int teams) {
        ReservationSlot slot = ReservationSlot.create(store, datetime);
        slot.addCapacity(2, teams);
        reservationSlotRepository.save(slot);   // 저장하면서 @PrePersist 가 ID 를 채움
        return slot.getCapacities().get(0).getSlotCapacityId();
    }

    protected static ReservationCreateRequest request(String capacityId) {
        ReservationCreateRequest request = new ReservationCreateRequest();
        ReflectionTestUtils.setField(request, "slotCapacityId", capacityId);
        return request;
    }

}

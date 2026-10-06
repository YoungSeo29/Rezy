package com.rezy.rezy.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@Configuration
public class RedisScriptConfig {

    // 스크립트를 빈으로 등록해 한 번만 읽고 재사용한다
    // 매 호출마다 새로 만들면 파일을 다시 읽고 SHA 도 다시 계산한다
    @Bean
    public DefaultRedisScript<Long> stockCasScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/stock-cas.lua"));
        script.setResultType(Long.class);   // 스크립트가 1 또는 0 을 반환
        return script;
    }

}

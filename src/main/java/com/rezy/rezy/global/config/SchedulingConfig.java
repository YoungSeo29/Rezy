package com.rezy.rezy.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

// Scheduled 를 동작 시키는 스위치
// test 프로파일에서는 켜지않음
@Configuration
@EnableScheduling
@Profile("!test")
public class SchedulingConfig {
}

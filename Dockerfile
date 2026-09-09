# ---------- 빌드 단계 ----------
FROM gradle:8.10-jdk17 AS build

WORKDIR /home/gradle/project

# 의존성 파일 먼저 복사 - 소스만 바뀌면 이 레이어는 캐시 재사용
COPY --chown=gradle:gradle build.gradle settings.gradle ./
COPY --chown=gradle:gradle src src

# 테스트는 제외하고 실행 가능한 jar 만 생성
RUN gradle bootJar --no-daemon -x test


# ---------- 실행 단계 ----------
# JDK 대신 JRE 를 써서 이미지 크기를 줄인다
FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /home/gradle/project/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
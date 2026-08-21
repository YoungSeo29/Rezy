package com.rezy.rezy.global;

import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// 요청 1건당 "경로 / 쿼리 수 / 소요시간" 을 한 줄로 출력 (측정용)
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryCountFilter extends OncePerRequestFilter {

    private final EntityManagerFactory entityManagerFactory;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        long queriesBefore = stats.getPrepareStatementCount();
        long start = System.currentTimeMillis();

        filterChain.doFilter(request, response);

        long queries = stats.getPrepareStatementCount() - queriesBefore;
        long took = System.currentTimeMillis() - start;

        log.info("[측정] {} {} → 쿼리 {}개, {}ms",
                request.getMethod(), request.getRequestURI(), queries, took);
    }
}

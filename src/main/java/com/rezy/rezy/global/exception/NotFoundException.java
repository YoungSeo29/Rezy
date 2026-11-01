package com.rezy.rezy.global.exception;

// 조회 대상이 없을 때. 요청 형식은 멀쩡하므로 400 이 아니라 404 로 내보낸다.
// JDK 의 NoSuchElementException 을 재사용하지 않는 이유는, Optional.get() 같은
// 코드 결함까지 404 로 둔갑시켜 숨기기 때문이다.
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}

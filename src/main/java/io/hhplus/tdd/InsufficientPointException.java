package io.hhplus.tdd;

public class InsufficientPointException extends RuntimeException {
    public InsufficientPointException() {
        super("잔고가 부족합니다.");
    }
}

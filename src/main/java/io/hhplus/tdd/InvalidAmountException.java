package io.hhplus.tdd;

public class InvalidAmountException extends RuntimeException {
    public InvalidAmountException(long amount) {
        super("금액은 0원 이상이어야 합니다. 입력된 값: " + amount);
    }
}

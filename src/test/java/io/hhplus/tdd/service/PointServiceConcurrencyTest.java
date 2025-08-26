package io.hhplus.tdd.service;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.PointHistory;
import io.hhplus.tdd.point.TransactionType;
import io.hhplus.tdd.point.UserPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class PointServiceConcurrencyTest {

    private final UserPointTable userPointTable = new UserPointTable();
    private final PointHistoryTable pointHistoryTable = new PointHistoryTable();
    private final PointService pointService = new PointService(userPointTable, pointHistoryTable);

    @Test
    void 동시_충전_동일_사용자_일관성() throws Exception {
        long userId = 10L;
        int threadCount = 10;
        long perCharge = 100L;
        long expectedPoint = 1000L;

        // 초기화
        userPointTable.insertOrUpdate(userId, 0L);

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    pointService.charge(userId, perCharge);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        boolean finished = done.await(5, TimeUnit.SECONDS);
        pool.shutdown();

        assertTrue(finished, "모든 사용 작업이 시간 내 완료되어야 합니다.");

        UserPoint finalPoint = pointService.getUserPoint(userId);
        assertEquals(expectedPoint, finalPoint.point(), "동시에 충전해도 최종 포인트는 합계와 일치해야 합니다.");

        List<PointHistory> histories = pointService.getPointHistory(userId);
        long chargeCount = histories.stream().filter(history -> history.type() == TransactionType.CHARGE).count();
        assertEquals(threadCount, chargeCount, "히스토리의 CHARGE 기록 수가 요청 수와 일치해야 합니다.");
    }



}

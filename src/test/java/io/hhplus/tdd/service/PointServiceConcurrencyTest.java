package io.hhplus.tdd.service;

import io.hhplus.tdd.InsufficientPointException;
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
        CountDownLatch ready = new CountDownLatch(threadCount);
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

        assertTrue(ready.await(5, TimeUnit.SECONDS), "스레드 준비 타임아웃");
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


    @Test
    void 동시_사용_초과_방지() throws Exception {
        long userId = 10L;
        int threadCount = 15;
        long perUse = 100L;
        long initial = 1000L;
        // 10건만 성공 가능, 5건은 잔고 부족

        userPointTable.insertOrUpdate(userId, initial);

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        List<Future<Boolean>> results = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            results.add(pool.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    pointService.use(userId, perUse);
                    return true;
                } catch (InsufficientPointException e) {
                    return false;
                } finally {
                    done.countDown();
                }
            }));
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS), "스레드 준비 타임아웃");
        start.countDown();
        boolean finished = done.await(5, TimeUnit.SECONDS);
        pool.shutdown();

        assertTrue(finished, "모든 사용 작업이 시간 내 완료되어야 합니다.");

        long success = 0;
        for (Future<Boolean> f : results) {
           success += f.get() ? 1 : 0;
        }

        assertEquals(initial / perUse, success, "성공한 사용 횟수는 초기 잔고 내에서만 허용되어야 합니다.");

        UserPoint finalPoint = pointService.getUserPoint(userId);
        assertEquals(initial - (success * perUse), finalPoint.point(), "최종 포인트는 성공한 사람의 합만큼 감소해야 합니다.");

        List<PointHistory> histories = pointService.getPointHistory(userId);
        long useCount = histories.stream().filter(history -> history.type() == TransactionType.USE).count();
        assertEquals(success, useCount, "히스토리의 USE 기록 수는 실제 성공 건수와 일치해야 합니다.");
    }

}

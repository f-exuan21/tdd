package io.hhplus.tdd.service;

import io.hhplus.tdd.InsufficientPointException;
import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.PointHistory;
import io.hhplus.tdd.point.TransactionType;
import io.hhplus.tdd.point.UserPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PointServiceTest {

    @Mock UserPointTable userPointTable;
    @Mock PointHistoryTable pointHistoryTable;

    PointService pointService;

    @BeforeEach
    void setUp() {
        pointService = new PointService(userPointTable, pointHistoryTable);
    }

    @Test
    void getUserPoint_조회() {
        // given
        long userId = 1L;

        when(userPointTable.selectById(userId))
                .thenReturn(new UserPoint(userId, 1_000L, 111L));

        // when
        UserPoint userPoint = pointService.getUserPoint(userId);


        // then
        /// 의도한 값으로 정상조회되는지 확인
        assertEquals(userId, userPoint.id());
        assertEquals(1_000L, userPoint.point());
        assertEquals(111L, userPoint.updateMillis());

        /// 의도하지 않은 로직 실행 방지
        verify(userPointTable).selectById(userId);
        verifyNoInteractions(pointHistoryTable);
        verifyNoMoreInteractions(userPointTable);
    }

    @Test
    void getPoinstHistory_조회() {
        // given
        long userId = 1L;

        when(pointHistoryTable.selectAllByUserId(userId))
                .thenReturn(List.of(
                        new PointHistory(1L, userId, 300L, TransactionType.CHARGE, 1_000L),
                        new PointHistory(2L, userId, 100L, TransactionType.USE, 1_000L),
                        new PointHistory(3L, userId, 200L, TransactionType.CHARGE, 1_000L)
                ));

        // when
        List<PointHistory> pointHistories = pointService.getPointHistory(userId);

        // then
        assertEquals(3, pointHistories.size());
        assertEquals(1L, pointHistories.get(0).id());
        assertEquals(300L, pointHistories.get(0).amount());
        assertEquals(2L, pointHistories.get(1).id());
        assertEquals(100L, pointHistories.get(1).amount());
        assertEquals(3L, pointHistories.get(2).id());
        assertEquals(200L, pointHistories.get(2).amount());

        verify(pointHistoryTable).selectAllByUserId(userId);
        verifyNoInteractions(userPointTable);
        verifyNoMoreInteractions(pointHistoryTable);

    }

    @Test
    void charge_포인트업데이트_히스토리기록() {
        // given
        long userId = 1L;
        long amount = 300L;

        UserPoint current = new UserPoint(userId, 1_000L, 100L);
        when(userPointTable.selectById(userId))
                .thenReturn(current);

        UserPoint updated = new UserPoint(userId, 1_300L, 200L);
        when(userPointTable.insertOrUpdate(userId, 1_300L))
                .thenReturn(updated);

        when(pointHistoryTable.insert(userId, amount, TransactionType.CHARGE, 200L))
                .thenReturn(new PointHistory(10L, userId, amount, TransactionType.CHARGE, 200L));

        // when
        UserPoint result = pointService.charge(userId, amount);

        // then
        assertEquals(result, updated);

        /// 호출 순서/인자 검증 : select -> update -> history
        InOrder inOrder = inOrder(userPointTable, pointHistoryTable);
        inOrder.verify(userPointTable).selectById(userId);
        inOrder.verify(userPointTable).insertOrUpdate(userId, 1_300L);
        inOrder.verify(pointHistoryTable).insert(userId, amount, TransactionType.CHARGE, 200L);
        inOrder.verifyNoMoreInteractions();

        /// 불필요 호출 없도록 추가 보증
        verifyNoMoreInteractions(userPointTable, pointHistoryTable);
    }

    @Test
    void use_포인트사용_히스토리기록() {
        // given
        long userId = 1L;
        long amount = 300L;

        UserPoint current = new UserPoint(userId, 1_000L, 100L);
        when(userPointTable.selectById(userId))
                .thenReturn(current);

        UserPoint updated = new UserPoint(userId, 700L, 200L);
        when(userPointTable.insertOrUpdate(userId, 700L))
                .thenReturn(updated);
        when(pointHistoryTable.insert(userId, amount, TransactionType.USE, 200L))
                .thenReturn(new PointHistory(10L, userId, amount, TransactionType.USE, 200L));

        // when
        UserPoint result = pointService.use(userId, amount);

        // then
        assertEquals(result, updated);

        InOrder inOrder = inOrder(userPointTable, pointHistoryTable);
        inOrder.verify(userPointTable).selectById(userId);
        inOrder.verify(userPointTable).insertOrUpdate(userId, 700L);
        inOrder.verify(pointHistoryTable).insert(userId, amount, TransactionType.USE, 200L);
        inOrder.verifyNoMoreInteractions();

        verifyNoMoreInteractions(userPointTable, pointHistoryTable);
    }

    @Test
    void use_요청금액이_보유포인트보다_적을_때() {

        // given
        long userId = 1L;
        long requestPoint = 300L;
        long currentPoint = 100L;

        UserPoint current = new UserPoint(userId, currentPoint, 100L);
        when(userPointTable.selectById(userId))
                .thenReturn(current);

        // when
        InsufficientPointException ex = assertThrows(
                InsufficientPointException.class,
                () -> pointService.use(userId, requestPoint)
        );

        // then
        assertTrue(ex.getMessage().contains("잔고가 부족합니다."));

        verify(userPointTable).selectById(userId);
        verifyNoMoreInteractions(userPointTable, pointHistoryTable);

    }


}
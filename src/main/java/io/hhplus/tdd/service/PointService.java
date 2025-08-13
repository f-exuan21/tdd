package io.hhplus.tdd.service;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.PointHistory;
import io.hhplus.tdd.point.TransactionType;
import io.hhplus.tdd.point.UserPoint;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Service
public class PointService {

    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;

    private final ConcurrentMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

    public <T> T withUserLock(long userId, Supplier<T> task) {
        ReentrantLock lock = locks.computeIfAbsent(userId, id -> new ReentrantLock());
        lock.lock();
        try {
            return task.get();
        } finally {
            lock.unlock();
            if (!lock.hasQueuedThreads()) locks.remove(userId, lock);
        }
    }

    public PointService(UserPointTable userPointTable, PointHistoryTable pointHistoryTable) {
        this.userPointTable = userPointTable;
        this.pointHistoryTable = pointHistoryTable;
    }

    public UserPoint getUserPoint(long userId) {
        return this.withUserLock(userId, () -> userPointTable.selectById(userId));
    }

    public List<PointHistory> getPointHistory(long userId) {
        return this.withUserLock(userId, () -> pointHistoryTable.selectAllByUserId(userId));
    }

    public UserPoint charge(long userId, long amount) {

        return this.withUserLock(userId, () -> {
            UserPoint current = userPointTable.selectById(userId);

            long newAmount = current.point() + amount;

            UserPoint updated = userPointTable.insertOrUpdate(userId, newAmount);
            pointHistoryTable.insert(userId, amount, TransactionType.CHARGE, updated.updateMillis());

            return updated;
        });

    }

    public UserPoint use(long userId, long amount) {

        return this.withUserLock(userId, () -> {
            UserPoint current = userPointTable.selectById(userId);

            if (current.point() < amount) throw new IllegalArgumentException("잔고가 부족합니다.");

            long newAmount = current.point() - amount;

            UserPoint updated = userPointTable.insertOrUpdate(userId, newAmount);
            pointHistoryTable.insert(userId, amount, TransactionType.USE, updated.updateMillis());

            return updated;
        });

    }
}

# 동시성 제어 

서비스 안정성과 데이터 정합성을 보장하기 위해 다양한 동시성 제어 기법을 분석하고, 프로젝트 환경에 적합한 방식을 찾고자 함

---

## 동시성 제어 방식의 분류

### 1. 비관적 락 (Pessimistic Lock)

---

- 트랜잭션 시작 시점부터 락을 걸어 다른 트랜잭션 접근 차단
- `SELECT ... FOR UPDATE` 또는 DB 격리 수준을 높여서 구현
- 충돌 가능성이 높을 때 안정적
- 락 경쟁으로 인한 성능 저하
- 적용 예시) 금융 거래, 재고 관리


### 2. 낙관적 락 (Optimistic Lock)

---

- 충돌 가능성을 낮게 보고, 커밋 시점에 버전 체크로 충돌 검출
- DB 에서는 단순히 버전 컬럼을 추가 / `WHERE id=? AND version=?` 조건을 활용 
- 락 오버헤드 없음
- 충돌 발생 시 재시도 로직 필요
- 적용 예시) 게시글 수정, 포인트 차감


### 3. 트랜잭션 격리 수준 (Isolation Level)

---

#### 1) 격리 수준이 필요한 이유
| 문제유형                  | 설명                       | 예시                                                          |
|-------------------------|--------------------------|-------------------------------------------------------------|
| **Dirty Read**          | 커밋되지 않은 데이터를 다른 트랜잭션이 읽음 | T1이 `price=200` 변경 후 커밋 전인데, T2가 그 값을 읽음                    |
| **Non-repeatable Read** | 같은 데이터를 두 번 읽는데 값이 다름    | T1이 `SELECT price` → T2가 값 변경 → T1이 다시 읽으면 값이 달라짐         |
| **Phantom Read**        | 같은 조건으로 조회했는데, 행의 개수가 달라짐 | T1 이 `price > 100` 조회 → T2 가 새 행 삽입 → T1이 다시 조회하면 결과 수 변경 |

#### 2) 격리 수준
- **READ UNCOMMITTED**
  - 단순히 버퍼 풀의 최신 값을 읽음 → 아직 커밋되지 않은 데이터를 읽을 수 있음 
  - 다른 트랜잭션이 롤백하면, 내가 읽었던 데이터는 실제로 존재하지 않게 됨 → 데이터 불일치 위험
  - 데이터 무결성 보장 어려움 → 거의 사용하지 않음
  - T1이 `price=200` 변경 (커밋 전) → T2 이 `price`를 읽음 (200) → T1이 롤백함 (**Dirty Read** 발생 가능)
- **READ COMMITED**
  - 매번 커밋된 최신 버전을 읽음 (오라클 기본 모드)
  - Dirty Read는 방지하지만, 같은 쿼리를 반복해 실행했을 때 결과가 달라질 수 있음 (**Non-Repeatable Read** 발생 가능)
- **REPEATABLE READ**
  - **같은 트랜잭션 안에서 동일한 조건으로 같은 데이터를 여러번 읽었을 때, 항상 같은 결과를 보장**하는 격리 수준
  - 트랜잭션이 시작된 시점의 데이터를 **스냅샷 처럼 고정**해두고, 그 트랜잭션이 끝날 때까지 변하지 않게 하는 방식
  - 동일한 레코드에 대해 여러 버전의 데이터가 존재한다 하여, **MVCC(Multi-Version Concurrency Control)** 이라고도 함
  - 트랜잭션 시작 시 **해당 시점의 스냅샷 (Undo log 기반)** 을 만들어 둠 
  - 이후 같은 트랜잭션 내에서 `SELECT` 를 수정하면 이 스냅샷을 기준으로 읽음 → 다른 트랜잭션이 데이터를 `UPDATE/DELETE` 해도 영향 없음
  - `INSERT` 된 새로운 행은 원래 보이는 경우가 있어 **Phantom Read**가 발생 가능
- **SERIALIZABLE**
  - `SELECT`도 사실상 `SELECT ... FOR SHARE/UPDATE` 처럼 락을 걸어 실행, 성능 저하 심하지만 정합성 최고
  - 가장 엄격한 격리 수준으로, 순차적으로 실행시킴 → 동시 처리 성능이 매우 떨어짐
  - 여러 트랜잭션이 동일한 레코드에 동시 접속할 수 없으므로, 어떠한 데이터 부정합 문제도 발생하지 않음 



### 4. 분산 락 (Redis, Zookeeper)

---

- **여러 서버(노드)나 프로세스가 공유하는 자원** 에 대해 동시에 접근 및 수정하지 못하도록 제어하는 락
- 단일 환경에서는 `synchronized` 나 `ReentrantLock` 으로 충분하지만, 다중 서버에서는 서버 메모리가 각각 달라 `공유된 외부 저장소` 사용


### 5. Java 동기화 (synchronized, Lock API)

---
#### 5-1. `synchronized` 키워드 
- 메소드나 코드 블록에 붙여서 **한 번에 하나의 스레드만** 실행 가능하게 함
- 예시
  ```java
  public synchronized void update() {
    // 임계 구역
  } 
  
  public void update() {
    synchronized(this) {
        // 임계 구역 
    }
  }
  ```
- 특징
  - 자동해제, 재진입 가능, JVM 레벨에서 보장

#### 5-2. `ReentrantLock` 클래스
- `synchronized` 보다 세밀한 제어 가능
- 락 획득 시 타임아웃 설정, 조건 변수(Condition) 사용 가능
- 예시
  ```java
  private final ReentrantLock lock = new ReentrantLock();
  
  public void update() {
    lock.lock();
    try {
      // 임계 구역
    } finally {
      lock.unlock();
    }
  }
  ```
- 특징
  - 재진입 가능
  - 락 획득 시 대기 시간 설정 가능 (`tryLock`)
  - 명시적으로 해제 필요 (`unlock`)
  - 
#### 5-3. `StampedLock` 클래스
- 읽기/쓰기 락을 분리 → 읽기 병행성 향상
- 예시
  ``` java
  private final StampedLock lock = new StampedLock();
  
  public void update() {
    long stamp = lock.writeLock();
    try {
      // 쓰기 작업 
    } finally {
      lock.unlockWrite(stamp);
    }
  }
  
  public void read() {
    long stamp = lock.readLock();
    try {
      // 읽기 작업
    } finally {
      lock.unlockRead(stamp);
    }
  }
   
  ```
- 특징
  - 재진입 불가능
  - 읽기 또는 쓰기 Lock 모드에서 반대 모드로 변경할 수 있다. 이를 `Upgrade` 라고 함
  - 낙관적 읽기 
    - `long stamp = lock.tryOptimisticRead();`
    - 공유자원 값을 락 없이 읽고, 끝에 `lock.validate(stamp)` 로 중간에 쓰기가 끼어들지 않았는지 검정 
    - 실패 시 `readLock()` 으로 폴백

>#### 재진입 정리 비교 
>| 구분 | 재진입 가능(Reentrant)      | 재진입 불가능(Non-Reentrant) |
>| -- | ---------------------- | -------------------- |
>| 동작 | 같은 스레드가 다시 락 요청 시 즉시 통과 | 같은 스레드도 다시 락 요청하면 대기 |
>| 장점 | 재귀 호출/내부 호출 시 안전       | 구현 단순, 락 사용 실수 쉽게 발견 |
>| 예시 | `synchronized`, `ReentrantLock` | `StampedLock`(기본), 일부 커스텀 락 |
> 
> - 재진입 가능 예시
> ```java
> public class Example {
>    private final ReentrantLock lock = new ReentrantLock();
>
>    public void outer() {
>        lock.lock();
>        try {
>            inner(); // 같은 스레드에서 다시 락 요청
>        } finally {
>            lock.unlock();
>        }
>    }
>
>    public void inner() {
>        lock.lock(); // 재진입 → 즉시 통과
>        try {
>            System.out.println("inner method");
>        } finally {
>            lock.unlock();
>        }
>    }
> }
> ```
> - 재진입 불가능 예시
> ``` java
> public void outer() {
>    nonReentrantLock.lock();
>    try {
>       inner(); // inner에서도 lock() 시도 → 자기 자신을 기다림 → 데드락
>    } finally {
>       nonReentrantLock.unlock();
>    }
> }
>
> ```


### 6. 메시지 큐 (MQ 기반 순차 처리)

---
공유 자원에 직접 여러 스레드/프로세스가 동시에 접근하지 않고,
모든 작업을 큐에 넣고 이를 단일 소미자(Consumer) 또는 순서가 보장된 소비자가 하나씩 처리하는 방식


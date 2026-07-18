package com.icc.qasker.global.query;

import java.util.ArrayDeque;
import java.util.Deque;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class RepoMethodContext {
  private static final ThreadLocal<Deque<String>> READ = ThreadLocal.withInitial(ArrayDeque::new);
  private static final ThreadLocal<String> TX_WRITE = new ThreadLocal<>();

  private RepoMethodContext() {}

  public static void push(String repoMethod) {
    READ.get().push(repoMethod);
  }

  public static void pop() {
    Deque<String> d = READ.get();
    if (!d.isEmpty()) {
      d.pop();
    }
  }

  public static String current() {
    Deque<String> d = READ.get();
    return d.isEmpty() ? null : d.peek();
  }

  public static String txWrite() {
    return TX_WRITE.get();
  }

  // ---- 조회 통일: read면 스택, write flush면 백업본 (호출측은 이거 하나만) ----
  public static String resolve() {
    String r = current();
    return r != null ? r : txWrite();
  }

  // 이 트랜잭션의 마지막 write repoMethod를 기록한다.
  // flush(커밋 직전)에서 CountingInspector가 resolve()로 꺼내 SQL 주석에 붙인다.
  public static void markTxWrite(String repoMethod) {
    TX_WRITE.set(repoMethod);

    // 트랜잭션이 끝나면 값을 지워, write 이름표가 트랜잭션 경계 밖으로 새지 않게 한다.
    //  - ThreadLocal은 스레드에 눌러앉아, 안 지우면 (1)같은 요청의 다음 트랜잭션 밖 읽기나
    //    (2)스레드 재사용 시 다음 요청에 이 값이 잘못 달라붙는다(오염).
    //  - afterCompletion은 커밋/롤백이 끝난 "뒤"라, flush가 값을 다 읽은 다음 안전하게 청소된다.
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
              // 트랜잭션 종료 → 스레드에서 제거
              TX_WRITE.remove();
            }
          });
    }
  }
}

package com.icc.qasker.global.query;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Spring Data 레포 메서드 호출을 가로채, "레포.메서드"(repoMethod)를 RepoMethodContext에 싣는다. CountingInspector가 이 값을
 * SQL 주석의 repoMethod=에 붙인다. @Profile("loadtest") 전용.
 */
@Aspect
@Component
@Profile("loadtest")
public class RepoMethodAspect {

  @Around("this(org.springframework.data.repository.Repository)")
  public Object tag(ProceedingJoinPoint pjp) throws Throwable {
    // 이 프록시가 어느 레포인지(예: BoardRepository) 이름을 뽑는다.
    //  왜 이렇게 뽑나:
    //   - save/findById 는 내 BoardRepository 가 아니라 상위 CrudRepository 에 정의돼 있다.
    //     그래서 "메서드가 정의된 곳"을 물으면 save 는 CrudRepository 로 나와, 어느 엔티티인지 잃는다.
    //   - 대신 프록시 객체가 실제로 구현한 레포 인터페이스를 보면 상속 메서드든 뭐든 항상 BoardRepository 다.
    //     proxiedUserInterfaces 는 그 프록시가 구현한 인터페이스 중 Spring 내부용을 걸러내고
    //     내가 만든 레포 인터페이스(BoardRepository)만 준다. 그 첫 번째가 우리가 원하는 레포다.
    Class<?>[] userItfs = AopProxyUtils.proxiedUserInterfaces(pjp.getThis());
    String repo = userItfs.length > 0 ? userItfs[0].getSimpleName() : "UnknownRepository";
    String method = pjp.getSignature().getName();
    // 예: "BoardRepository.findByCategory"
    String repoMethod = repo + "." + method;

    // 읽기: 실행 중 참조
    RepoMethodContext.push(repoMethod);
    if (isWrite(method)) {
      RepoMethodContext.markTxWrite(repoMethod);
    }
    try {
      return pjp.proceed();
    } finally {
      RepoMethodContext.pop();
    }
  }

  private static boolean isWrite(String method) {
    return method.startsWith("save")
        || method.startsWith("delete")
        || method.startsWith("insert")
        || method.startsWith("update")
        || method.startsWith("remove")
        || method.startsWith("persist")
        || method.startsWith("flush");
  }
}

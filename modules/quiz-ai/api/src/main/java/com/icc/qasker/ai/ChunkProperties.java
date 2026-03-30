package com.icc.qasker.ai;

import java.util.List;

/** A/B 테스트용 청크 분할 설정. quiz-make-impl에서도 참조하므로 api 모듈에 위치한다. */
public interface ChunkProperties {

  List<Integer> getMaxCountVariants();

  int pickMaxCount();
}

package com.icc.qasker.document.service;

import com.icc.qasker.document.ConvertService;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * LibreOffice(JODConverter)가 비활성화된 환경에서 사용되는 no-op 구현체. CI/테스트 환경처럼 DocumentConverter 빈이 없을 때 자동
 * 활성화된다.
 */
@Slf4j
@Service
@ConditionalOnProperty(
    prefix = "jodconverter.local",
    name = "enabled",
    havingValue = "false",
    matchIfMissing = true)
public class NoOpConvertService implements ConvertService {

  @Override
  public Path convertToPdf(Path inputFile) {
    log.warn("[문서 변환 비활성화] LibreOffice 미설치 환경에서 변환 요청 수신");
    throw new UnsupportedOperationException("문서 변환이 비활성화된 환경입니다.");
  }
}

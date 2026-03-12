package com.icc.qasker.aws;

import java.nio.file.Path;

/** 문서 파일을 PDF로 변환하는 서비스 인터페이스. 순수 로컬 파일 변환만 담당하며, S3 등 외부 저장소 의존성이 없다. */
public interface ConvertService {

  /**
   * 로컬 파일을 PDF로 변환한다.
   *
   * @param inputFile 변환할 원본 파일의 로컬 경로
   * @return 변환된 PDF 파일의 로컬 경로
   */
  Path convertToPdf(Path inputFile);
}

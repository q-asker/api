package com.icc.qasker.global.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class CustomException extends RuntimeException {

  private final HttpStatus httpStatus;
  private final String message;
  private final String context;

  // 기본 사용: 정의된 예외 메시지만으로 충분할 때
  // 예) throw new CustomException(ExceptionMessage.NOT_FOUND);
  // 로그) ERROR 해당 리소스를 찾을 수 없습니다
  //       com.icc.qasker.global.error.CustomException: 해당 리소스를 찾을 수 없습니다
  //         at ...Service.method(Service.java:30)
  public CustomException(ExceptionMessage exceptionMessage) {
    super(exceptionMessage.getMessage());
    this.httpStatus = exceptionMessage.getHttpStatus();
    this.message = exceptionMessage.getMessage();
    this.context = null;
  }

  // 동적 메시지: 정의되지 않은 상태 코드와 메시지를 직접 지정할 때
  // 예) throw new CustomException(HttpStatus.BAD_REQUEST, "파일 크기가 10MB를 초과합니다");
  // 로그) ERROR 파일 크기가 10MB를 초과합니다
  //       com.icc.qasker.global.error.CustomException: 파일 크기가 10MB를 초과합니다
  //         at ...Service.method(Service.java:30)
  public CustomException(HttpStatus httpStatus, String message) {
    super(message);
    this.httpStatus = httpStatus;
    this.message = message;
    this.context = null;
  }

  // 디버깅 컨텍스트 포함: 로그에 부가 정보(ID, 파라미터 등)를 남길 때
  // 예) throw new CustomException(ExceptionMessage.NOT_FOUND, "problemSetId=" + id);
  // 로그) ERROR [problemSetId=5] 해당 리소스를 찾을 수 없습니다
  //       com.icc.qasker.global.error.CustomException: 해당 리소스를 찾을 수 없습니다
  //         at ...Service.method(Service.java:30)
  public CustomException(ExceptionMessage exceptionMessage, String context) {
    super(exceptionMessage.getMessage());
    this.httpStatus = exceptionMessage.getHttpStatus();
    this.message = exceptionMessage.getMessage();
    this.context = context;
  }

  // 원인 체이닝: 외부 라이브러리 예외를 래핑하여 원인을 보존할 때
  // 예) catch (IOException e) { throw new CustomException(ExceptionMessage.FILE_CONVERT_FAIL, e); }
  // 로그) ERROR 파일 변환에 실패했습니다
  //       com.icc.qasker.global.error.CustomException: 파일 변환에 실패했습니다
  //         at ...Service.method(Service.java:30)
  //       Caused by: java.io.IOException: Stream closed
  //         at java.io.FileInputStream.read(FileInputStream.java:233)
  public CustomException(ExceptionMessage exceptionMessage, Throwable cause) {
    super(exceptionMessage.getMessage(), cause);
    this.httpStatus = exceptionMessage.getHttpStatus();
    this.message = exceptionMessage.getMessage();
    this.context = null;
  }

  // 컨텍스트 + 원인 체이닝: 디버깅 정보와 원인 예외를 모두 보존할 때
  // 예) catch (S3Exception e) { throw new CustomException(ExceptionMessage.FILE_UPLOAD_FAIL,
  // "bucket=" + bucket, e); }
  // 로그) ERROR [bucket=my-bucket] 파일 업로드에 실패했습니다
  //       com.icc.qasker.global.error.CustomException: 파일 업로드에 실패했습니다
  //         at ...Service.method(Service.java:30)
  //       Caused by: RuntimeException: OCI Object Storage 업로드 실패
  //         at ...S3Client.putObject(S3Client.java:120)
  public CustomException(ExceptionMessage exceptionMessage, String context, Throwable cause) {
    super(exceptionMessage.getMessage(), cause);
    this.httpStatus = exceptionMessage.getHttpStatus();
    this.message = exceptionMessage.getMessage();
    this.context = context;
  }
}

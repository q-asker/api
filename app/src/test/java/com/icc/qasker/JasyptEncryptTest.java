package com.icc.qasker;

import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.iv.RandomIvGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Jasypt 암호화/복호화 유틸리티 테스트.
 *
 * <p>사용법:
 *
 * <ol>
 *   <li>환경변수 JASYPT_ENCRYPTOR_PASSWORD 설정
 *   <li>input 값을 변경 후 encrypt() 또는 decrypt() 실행
 *   <li>콘솔 출력값을 yml에 ENC(출력값) 형태로 붙여넣기
 * </ol>
 */
class JasyptEncryptTest {

  private PooledPBEStringEncryptor encryptor;

  @BeforeEach
  void setUp() {
    String password = System.getenv("JASYPT_ENCRYPTOR_PASSWORD");
    Assumptions.assumeTrue(
        password != null && !password.isBlank(), "JASYPT_ENCRYPTOR_PASSWORD 미설정 — 로컬 전용 테스트 skip");

    encryptor = new PooledPBEStringEncryptor();
    encryptor.setPassword(password);
    encryptor.setAlgorithm("PBEWITHHMACSHA512ANDAES_256");
    encryptor.setIvGenerator(new RandomIvGenerator());
    encryptor.setPoolSize(1);
  }

  @Test
  void encrypt() {
    String input = "암호화할 값을 여기에 입력";

    String encrypted = encryptor.encrypt(input);
    System.out.println("===========================================");
    System.out.println("원본: " + input);
    System.out.println("암호화: ENC(" + encrypted + ")");
    System.out.println("===========================================");
  }

  @Test
  void decrypt() {
    String input = "복호화할 ENC() 안의 값을 여기에 입력";

    String decrypted = encryptor.decrypt(input);
    System.out.println("===========================================");
    System.out.println("암호화된 값: " + input);
    System.out.println("복호화: " + decrypted);
    System.out.println("===========================================");
  }
}

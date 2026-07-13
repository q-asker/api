package com.icc.qasker.auth.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Refresh Token에 사용하는 암호화 유틸리티. SHA-256 hex 해시와 URL-safe Base64 난수 생성을 담당한다. */
public final class TokenCrypto {

  private static final SecureRandom RAND = new SecureRandom();

  private TokenCrypto() {}

  public static String randomUrlSafe(int bytes) {
    byte[] buf = new byte[bytes];
    RAND.nextBytes(buf);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
  }

  public static String sha256Hex(String v) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] d = md.digest(v.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(d.length * 2);
      for (byte b : d) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}

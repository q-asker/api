package com.icc.qasker.global.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.icc.qasker.global.error.CustomException;
import org.hashids.Hashids;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("HashUtil 인코딩/디코딩 검증")
class HashUtilTest {

  private final HashUtil hashUtil = new HashUtil(new Hashids("test-salt", 6));

  @ParameterizedTest
  @ValueSource(longs = {1L, 42L, 12345L, 9_999_999L})
  @DisplayName("encode 후 decode 하면 원래 id로 복원된다 (왕복 항등)")
  void encodeDecodeRoundTrip(long id) {
    String encoded = hashUtil.encode(id);

    assertThat(hashUtil.decode(encoded)).isEqualTo(id);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "!!!invalid!!!", "###"})
  @DisplayName("디코딩 결과가 비어 있으면 CustomException을 던진다")
  void decodeInvalidThrows(String invalid) {
    assertThatThrownBy(() -> hashUtil.decode(invalid)).isInstanceOf(CustomException.class);
  }
}

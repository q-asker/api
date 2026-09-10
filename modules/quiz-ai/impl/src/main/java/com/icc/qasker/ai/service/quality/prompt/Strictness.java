package com.icc.qasker.ai.service.quality.prompt;

import java.util.Locale;

/** 검증 항목 엄격도(FR-011). yml에서 항목마다 지정한다. */
public enum Strictness {

  /** 경미한 위반도 미달. */
  STRICT,

  /** 명백한 위반만 미달(애매하면 통과). */
  NORMAL,

  /** 미적용 — 프롬프트에 싣지 않는다. */
  OFF;

  /** 프롬프트에 찍히는 표기. yml 값과 같다. */
  public String label() {
    return name().toLowerCase(Locale.ROOT);
  }
}

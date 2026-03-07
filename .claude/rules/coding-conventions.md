# 코딩 컨벤션

## 일반 규칙

- 변수명/함수명: 영어 (camelCase)
- 컴포넌트명: PascalCase
- 상수: SCREAMING_SNAKE_CASE
- 파일명: kebab-case (컴포넌트 파일 포함)
- 코드 주석: 한국어
- 경로 alias: `#` 접두사 (Node.js subpath imports, `package.json`의 `imports` 필드)

## 컴포넌트 규칙

- 함수형 컴포넌트만 사용 (arrow function)
- Props 타입은 interface로 정의, 파일 상단에 배치
- default export 사용
- 각 feature/page/widget은 `index.tsx`(UI) + `index.ts`(re-export) 패턴
- shadcn/ui 컴포넌트는 `components/ui/`에 위치, 직접 수정 금지

## 스타일링 규칙

- Tailwind CSS 유틸리티 클래스 사용
- `cn()` 함수로 조건부 클래스 병합
- 인라인 스타일(`style` 속성) 사용 금지

## 상태 관리

- Zustand store: feature 단위로 분리 (`model/` 디렉토리)
- 커스텀 훅으로 비즈니스 로직 캡슐화 (`use*.ts`)

## Import 순서

1. React/외부 라이브러리
2. 내부 모듈 (`#` alias)
3. 상대경로
4. 타입 import (`type` 키워드 사용)

## 구현 방식 선택

- UI 컴포넌트: shadcn/ui에 해당 컴포넌트가 있으면 반드시 사용한다
- i18n: 모든 사용자 노출 텍스트는 `useTranslation` 훅의 `t()` 함수 사용
- API 통신: `#shared/api`의 Axios 인스턴스 사용
- 토스트: `#shared/toast`의 CustomToast 사용
- 비동기: async/await 사용 (Promise chain 금지)

> 금지 사항은 `constraints.md` 참조.

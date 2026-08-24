# TRAFFIC CITY 2 Android shell

이 추가 파일은 현재 GitHub Pages 주소를 여는 Android WebView 앱과 GitHub Actions APK 빌드 설정입니다.

고정 게임 주소:
`https://byanka-rail.github.io/traffic-city-2/`

## 저장소에 추가

이 압축파일을 푼 뒤 저장소 루트에 다음 두 항목을 그대로 올립니다.

- `.github/`
- `android/`

기존 `index.html`은 그대로 둡니다.

최종 구조:

```text
traffic-city-2/
├─ index.html
├─ .github/
│  └─ workflows/
│     └─ build-android.yml
└─ android/
   ├─ settings.gradle
   ├─ build.gradle
   ├─ gradle.properties
   └─ app/
      └─ ...
```

## APK 만들기

파일을 Commit하면 Android 관련 파일 변경으로 GitHub Actions가 자동 실행됩니다.

GitHub 저장소에서:

`Actions → Build TRAFFIC CITY 2 APK → 가장 최근 실행 → Artifacts → TRAFFIC_CITY_2-APK`

을 열어 APK 압축을 내려받습니다.

수동으로 다시 빌드하려면:

`Actions → Build TRAFFIC CITY 2 APK → Run workflow`

을 누릅니다.

## 이후 게임 업데이트

Android 파일은 수정하지 않습니다.

새 HTML을 완성할 때마다 GitHub의 기존 `index.html`만 새 HTML로 덮어쓰고 Commit합니다.
GitHub Pages가 갱신되면 설치된 APK도 다음 실행부터 새 HTML을 불러옵니다.

APK는 매 실행 시 문서 URL에 시간 쿼리를 붙여 HTML 캐시를 우회하지만, origin은 같은
`https://byanka-rail.github.io`이므로 해당 Pages 주소의 localStorage 세이브는 유지됩니다.

## 포함 기능

- JavaScript / localStorage
- GitHub Pages 최신 HTML 로드
- 시작 문서 캐시 우회
- 같은 Pages 경로는 앱 내부에서 유지
- 외부 링크는 기본 브라우저로 열기
- JSON 등 파일 불러오기용 Android 파일 선택기
- 일반 HTTPS 다운로드
- blob 다운로드를 Android Downloads에 저장하는 JS bridge
- 네트워크 오류 안내 및 다시 연결
- 화면회전 시 WebView 상태 보존
- HTTPS 인증서 오류 우회 금지


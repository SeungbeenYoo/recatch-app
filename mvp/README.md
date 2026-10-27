# ReCatch MVP 시제품

기획서 STEP 1(Android 알림 파싱 베타)의 시제품입니다.

```
[Android 앱]  카드 승인·취소 알림 수집 → 폰 안에서 파싱 (금액·가맹점·카드 끝자리·시각만)
      │  POST /api/events   (알림 원문·이름은 보내지 않음)
      ▼
[백엔드]      가맹점 정규화 → 혜택 룰 계산 → 재결제 추천 → 취소·재승인으로 자동 판정 → 월 정산
      │  응답(추천·판정 결과)
      ▼
[Android 앱]  "2,509원 더 아낄 수 있어요" 알림 → 남은 시간·재결제 방법·수수료 화면 → 추천 내역·이번 달 정산
```

전체 구현 계획은 [../docs/implementation-plan.md](../docs/implementation-plan.md)에 있습니다.

## 1. 백엔드 실행 (Windows·Linux 공통, Node 22 이상, 설치할 패키지 없음)

```sh
cd mvp
npm test        # 코어·서버 테스트 44개
npm start       # http://0.0.0.0:8787, 시작할 때 폰에서 쓸 PC 주소 후보를 출력
```

| 환경 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `PORT` | `8787` | |
| `HOST` | `0.0.0.0` | PC 안에서만 쓰려면 `127.0.0.1` |
| `RECATCH_DB` | `server/.data/db.json` | 시제품 저장소(JSON 파일). 실서비스는 DB로 교체 |
| `RECATCH_INVITE` | 없음 | 설정하면 앱 등록 때 초대 코드 필요 (파일럿용) |

폰 없이 연동 확인: 서버를 켠 상태에서 다른 터미널에서

```sh
node server/tools/simulate-phone.js http://127.0.0.1:8787
```

앱과 똑같이 "폰에서 파싱 → 필드만 전송"을 하고, 서버 응답(추천 알림·판정·정산)을 출력합니다.

### API

| 메서드 | 경로 | 내용 |
| --- | --- | --- |
| GET | `/api/health` | 상태 확인 (인증 없음) |
| POST | `/api/register` | 기기 등록 → `{ userId, token }` (토큰은 서버에 해시로만 저장) |
| GET | `/api/catalog` | 카드·멤버십 상품 목록 |
| GET/PUT | `/api/wallet` | 보유 카드(상품·끝자리·전월 실적·이번 달 사용액·혜택별 사용액), 멤버십(상품·등급·바코드 번호·형식, 또는 혜택 정보 없는 직접 추가), 조건(학생·지역·연령). 바코드 번호는 웹 시연의 `wallet-validation.js`와 같은 규칙(`server/barcode-rules.js`)으로 검사. 새 사용자는 예시 지갑으로 시작 |
| POST | `/api/events` | 파싱된 승인·취소 1건 `{ type, issuer, last4, amount, merchantRaw, at, cumulative, receivedAt }` → `{ event, recommendation, reason, updates }` |
| GET | `/api/recommendations` | 추천 목록(최신순), 시간이 지난 건은 이때 판정 |
| GET | `/api/recommendations/:id` | 추천 1건 |
| POST | `/api/recommendations/:id/baseline` | "이미 멤버십 썼어요" `{ membershipId }` → 기준선 재계산 |
| POST | `/api/recommendations/:id/skip` | "이번엔 넘어갈게요" → 놓친 혜택 (수수료 없음) |
| POST | `/api/recommendations/:id/confirm` | 캐시백·청구할인 확정 `{ confirmedLater }` → `pending` → `done` |
| GET | `/api/statement?month=YYYY-MM` | 월 정산 (완료 건 차액의 20%, 첫 달 쿠폰). 누적 청구액이 1만 원 미만이면 다음 달로 넘김 (`carriedIn`·`totalDue`·`billed`·`dueDate`) |

인증은 `Authorization: Bearer <token>`. 본문 32KB 제한, 입력값 형식 검사.

## 2. Android 앱

위치: `android/` (Java, 외부 라이브러리 없음, minSdk 26 · targetSdk 35)

```sh
android\build.cmd        # Windows (이 PC의 E:\bt 툴체인 사용, 오프라인이면 --offline)
./android/build.sh       # Linux/macOS (ANDROID_HOME, Gradle 8.9+, JDK 17+)
```

결과: `android/app/build/outputs/apk/debug/app-debug.apk`

화면은 웹 시연(`index.html`)과 같은 구조이고, 숫자는 모두 서버 계산 결과입니다.

| 화면 | 내용 |
| --- | --- |
| 상단 | `recatch.` 로고, 결제 감지 상태(켜짐/꺼짐), 설정 |
| 홈 | 이번 달 되찾은 돈·수수료·남은 돈, 정산 명세서 / 지금 안내 중인 재결제(남은 시간) 또는 "재결제 체험하기" / 내 지갑 바로 쓰기(카드·멤버십 바코드) / 내 소비 한눈에 / 최근 리캐치 / 이번 주 챙길 혜택(전월 실적 남은 금액, 한도 거의 씀, 자주 놓치는 순간) |
| 내역 | 상태별 개수, 작은 탭(전체·완료·확인 중·놓친 혜택), 목록 |
| 분석 | 재결제 성공률, 놓친 혜택, 월별 되찾은 돈, 어디서 아꼈나요, 많이 아껴준 혜택, 자주 놓치는 순간 |
| 지갑 | 카드 레일 + 선택한 카드의 전월 실적·이번 달 사용·혜택 한도, 멤버십·바코드, 나에게 해당하는 조건(학생·통신사 등급·지역·연령) |
| 시트 | 정산 명세서, 설정(서버 연결·알림 스위치 4개·테스트 문자), 혜택 지갑에 추가(멤버십/카드/통신사), 카드 정보·수정, 멤버십 바코드(최대 밝기), 조건 변경 |
| 재결제 화면 | 혜택 배지, 멤버십 바코드, 남은 시간, 직원 안내, 지금 vs 재결제, 경고, 수수료 / 다시 결제했어요 · 이번엔 넘어갈게요 · 이미 멤버십 썼어요 · (시연용) 취소·재결제 알림 보내기 / 완료 화면 |

화면은 좌우로 밀어 홈 → 내역 → 분석 → 지갑으로 넘어가고, 내역에서는 작은 탭이 먼저 바뀝니다. 다크 모드는 시스템 설정을 따릅니다.

### 에뮬레이터로 보기 (이 PC 기준)

`E:\bt\sdk`에 Android 15 x86_64 이미지와 AVD `recatch35`(AVD 폴더 `E:\bt\avd`)가 있습니다. Windows PowerShell:

```powershell
$env:ANDROID_SDK_ROOT='E:\bt\sdk'; $env:ANDROID_AVD_HOME='E:\bt\avd'; E:\bt\sdk\emulator\emulator.exe -avd recatch35 -no-snapshot
```

```powershell
E:\bt\sdk\platform-tools\adb.exe install -r android\app\build\outputs\apk\debug\app-debug.apk
```

에뮬레이터에서 서버 주소는 `http://10.0.2.2:8787`(앱 기본값)입니다. 확인한 화면은 `android/screenshots/`에 있습니다.

### 써 보는 순서

1. PC에서 `npm start` → 출력된 `http://192.168.x.x:8787` 확인 (폰과 같은 Wi-Fi, Windows 방화벽에서 Node 허용)
   - USB 연결이면 `adb reverse tcp:8787 tcp:8787` 후 `http://127.0.0.1:8787`, 에뮬레이터는 `http://10.0.2.2:8787`
2. 앱 설치 → 상단 **설정** → 서버 주소 입력 → 연결하고 등록
3. 설정의 **결제 알림 실시간 감지**를 켜고 시스템 설정에서 리캐치 알림 접근 허용
   - APK 직접 설치 + Android 13 이상에서 막히면: 설정 → 앱 → 리캐치 시제품 → ⋮ → 제한된 설정 허용
4. 홈 **재결제 체험하기** → 추천 알림 → 재결제 화면 → "체험: 취소·재결제 알림 보내기" → 확인 중 → 적립 확인 → 완료
5. 지갑에서 실제 쓰는 카드(끝자리·전월 실적)와 멤버십 바코드를 등록하면 실제 카드 알림이 같은 경로로 처리됩니다.

| 파일 | 하는 일 |
| --- | --- |
| `MainActivity` · `SwipeFrame` | 상단·4개 화면·하단 탭, 좌우 밀기 전환, 5초마다 서버 데이터 갱신 |
| `HomeTab` · `HistoryTab` · `StatsTab` · `WalletTab` | 각 화면 |
| `Sheets` | 명세서·설정·지갑 추가/수정·카드 정보·멤버십 바코드·조건 시트 |
| `RecommendationActivity` | 재결제 화면·완료 화면 |
| `Repo` · `Insights` · `Parts` · `Ui` | 서버 데이터 보관, 분석 계산, 공통 조각, 웹 시연 색·모양 |
| `Barcode` · `BarcodeView` | 멤버십 바코드 (Code 128, EAN-13, UPC-A). `python android/tools/check_barcode.py`로 표준 판독 확인 |
| `CardNotificationListener` · `CardParser` · `EventSender` · `Notifier` | 알림 수신 → 폰에서 파싱 → 필드만 전송 → 추천 알림 |
| `Demo` | 재결제 체험용 결제 문자 생성 (실제 결제 없음, 같은 경로로 처리) |

## 3. 코어 (앱·서버 공용 판단 로직)

```sh
npm run demo             # 서버 없이 시나리오 실행
node cli/demo.js "[Web발신]\nKB국민체크(9421)\n홍*동님\n10/16 19:44\n74,000원\n빕스동대구역점 사용"
```

| 파일 | 하는 일 |
| --- | --- |
| `core/parser.js` | 승인·취소 문자/푸시에서 종류·카드사·끝자리·금액·가맹점·시각·누적 실적만 추출. 이름·원문은 버림 |
| `core/merchant.js` | 가맹점명 정규화(지점명 분리, 별칭) → 브랜드·업종 |
| `core/engine.js` | 보유 카드 × 멤버십 조합 계산. 전월 실적·월 한도·시간대·요일·최소 금액 조건, 멤버십 현장할인 후 카드 혜택 중복. 기준선(원래 결제) 대비 차액이 1,000원 이상이고 2분 안일 때만 추천. 실적 미달·체크카드 환불 지연·캐시백 확정 대기 경고 |
| `core/matcher.js` | 추천 뒤 들어온 취소·재승인으로 재결제 여부 자동 판정 (`done`/`pending`/`missed`/`abandoned`/`double_paid`). 현장할인은 재승인 금액으로 확인하고 덜 깎인 만큼 차감 |
| `core/settlement.js` | 완료 건 차액의 20% 수수료(확정), 첫 달 1,000원 쿠폰(수수료 이하), 프리미엄 월 2,900원 |
| `core/pipeline.js` | 위 단계를 묶은 객체. 원문은 `onNotification`, 파싱된 필드는 `onParsed`. 상태는 JSON으로 저장·복원 |
| `data/*.json` | 시연용 가맹점 사전, 카드·멤버십 혜택 룰, 예시 지갑, 합성 알림 샘플 |

## 시제품의 한계

- 혜택 값은 웹 시연과 같은 **예시**(`verified: false`). 실제 약관 확인 후 `verified: true`와 `source`를 채울 것.
- 알림 형식은 추정. 팀원 실제 승인 문자를 가려서 `data/notification-samples.json`에 넣으면 JS·Java 파서 테스트에 함께 쓰입니다.
- HTTP(평문)로 같은 Wi-Fi에서만 쓰는 구성. 외부 공개·실사용 전에는 HTTPS, 등록 제한, DB, 백업이 필요합니다.
- 서버에는 카드 끝 4자리와 가맹점명(지점 포함)이 저장됩니다(계산·판정에 필요). 이벤트는 24시간 뒤 삭제, 원문·이름은 저장하지 않습니다.
- 앱은 예시 지갑으로 시작합니다. 지갑 화면에서 실제 카드·멤버십으로 바꾸세요.
- 웹 시연의 '영남대 주변 혜택 거점' 지도는 현장 검증 전이라 앱에 넣지 않았습니다.
- 앱 화면은 에뮬레이터(Android 15)에서 홈·내역·분석·지갑·시트·재결제·완료·다크 모드까지 확인했습니다. 실제 카드 알림 수신(알림 접근 권한)과 실기기는 아직 확인하지 않았습니다.
- 혜택 한도 사용액은 승인·취소 알림으로 쌓고 빼며, 달이 바뀌면 이번 달 사용액이 전월 실적으로 넘어갑니다. 멤버십 현장할인을 받았는지는 알 수 없어 카드 혜택만 셉니다.

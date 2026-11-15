# 독립 AI 알고리즘 실험

운영 서비스와 분리한 학습 및 평가 코드다. `src/`, API, DB, 프론트, Gradle, CI와 배포 설정에 연결하지 않는다. 현재 Dockerfile은 이 폴더를 이미지에 복사하지 않는다. 학습과 테스트는 명시적으로 로컬에서만 실행한다.

## 구현 범위와 현재 상태

| 실험 | 직접 구현한 알고리즘 | 학습 입력 | 현재 검증 |
| --- | --- | --- | --- |
| 지역 인구 특성 | 표준화, K-means++ 초기화, 10회 재시작, silhouette | 지역별 인구 및 세대 CSV | 2026년 10월 말 용인시 실제 39개 읍면동 학습 및 합계 검증 완료 |
| 농업 문의 분류 | 한국어 문자 2~4-gram, Multinomial Naive Bayes, Laplace smoothing | 사람이 분류한 비식별 문의 CSV | 합성 문장으로 학습 및 별도 평가 흐름 검증, 실제 문의 학습 미완료 |
| 토마토 이미지 분류 기초 모델 | RGB 채널별 8구간 히스토그램, 클래스별 평균 중심, 제곱 거리 분류 | 사람이 분류한 사진과 manifest CSV | 합성 픽셀 및 선택적 이미지 디코딩 검사, 실제 토마토 병해 학습 미완료 |

이미지 모델은 CNN이 아니다. 사진의 색상 분포를 쓰므로 배경, 조명, 촬영 구도가 결과를 좌우한다. 병해 진단 성능이나 관련 없는 사진을 거부하는 능력을 제공하지 않는다. 텍스트 모델의 점수도 보정된 신뢰도가 아니다. 학습 어휘가 없는 문장은 미분류로 반환하지만 일부 어휘가 겹치는 엉뚱한 문장은 잘못 분류할 수 있다.

## 로컬 실행

Python 3.10 이상을 사용한다. 지역 군집화와 텍스트 분류는 추가 패키지가 없다.

```powershell
python -m unittest discover -s experiments/offline_ai -p "test_*.py" -v
python experiments/offline_ai/demo.py build/offline-ai-demo
```

이미지 파일을 읽으려면 로컬 실험 전용 가상환경에 Pillow를 설치한다. 운영 서버나 서비스 이미지에는 설치하지 않는다. Pillow가 없으면 이미지 디코딩 검사만 skip으로 표시되고 픽셀 특성 계산과 분류 알고리즘 검사는 실행된다.

```powershell
python -m venv build/offline-ai-venv
& build/offline-ai-venv/Scripts/python.exe -m pip install -r experiments/offline_ai/requirements-images.txt
& build/offline-ai-venv/Scripts/python.exe -m unittest discover -s experiments/offline_ai -p "test_*.py" -v
& build/offline-ai-venv/Scripts/python.exe experiments/offline_ai/demo.py build/offline-ai-demo --with-images
```

`demo.py`는 `synthetic`으로 표시한 임의 지역, 문장 및 단색 이미지를 생성한다. 토마토 이미지나 실제 용인시 데이터로 소개해서는 안 된다. 실제 AI 서비스나 외부 API를 호출하지 않는다. 결과 파일은 기본 `.gitignore`에서 제외되는 `build/` 아래에 보관한다.

## 실제 데이터로 학습하기

지역 분석은 [REGIONS.md](REGIONS.md)의 동일 기준일 데이터 결합 조건을 따른다. 출처는 [용인시 월별 인구통계](https://www.yongin.go.kr/user/bbs/BD_selectBbsList.do?q_bbsCode=1227)다. 공식 XLSX 두 파일을 확보해 2026년 10월 말 기준 39개 읍면동으로 결합했다. 실제 결과와 검증 내역은 [결과 설명](results/yongin-2026-10/README.md), [가공 CSV](results/yongin-2026-10/regions.csv), [모델 JSON](results/yongin-2026-10/model.json)에 있다. 원본 XLSX는 저장소에 재배포하지 않는다.

텍스트 출처 후보는 [용인시 농업기술센터 질의 게시판](https://www.yongin.go.kr/user/bbs/BD_selectBbsList.do?q_bbsCode=1056)이다. 공개 열람 가능하다는 이유만으로 자동 수집과 재학습이 허용되는 것은 아니다. 이용 조건을 확인하고 이름, 연락처, 주소, 계정 및 원문 속 식별 정보를 제거한 뒤 주제별 라벨 기준을 정해 사람이 검토한다. 이 저장소에는 게시물 수집기, 게시물 원문 및 개인 식별 정보를 넣지 않았다.

텍스트 CSV 열 순서:

```csv
id,group,text,label
```

`group`은 원본 게시글 및 답글 묶음처럼 같은 내용을 공유하는 단위다. 같은 그룹이 여러 라벨에 걸치면 현재 구현은 거부하므로 라벨 정책과 다중 주제 글 처리 기준을 먼저 정해야 한다. 문장 1개를 단어 몇 개만 바꿔 여러 그룹으로 만들면 안 된다.

이미지 출처 후보는 [AI Hub 작물 질병 이미지](https://www.aihub.or.kr/aihubdata/data/view.do?currMenu=115&dataSetSn=150&topMenu=100)다. 용인시에서 촬영한 데이터라고 확인하지 않았으므로 용인시 데이터라고 소개하지 않는다. 다운로드 권한과 이용 조건, 토마토에 해당하는 라벨을 확인하고 실제 파일을 확보해야 한다. 이 저장소에는 AI Hub 원본을 포함하지 않았다.

이미지 manifest CSV 열 순서:

```csv
id,group,path,label
```

`path`는 manifest 폴더 아래의 상대 경로다. `group`은 같은 식물, 촬영 세션 및 연사 이미지 묶음으로 지정한다. 같은 사진의 재인코딩 및 유사 사진까지 자동 탐지하지는 않으므로 사람이 그룹을 검토해야 한다. 단일 프레임 JPEG, PNG, WebP, 파일당 10MB 및 1600만 화소 이하를 허용한다.

실제 비식별 자료를 준비한 뒤 다음 명령을 사용한다. 아래 데이터 경로는 준비해야 할 입력 예시이며 저장소에 존재하는 실제 자료가 아니다.

```powershell
python experiments/offline_ai/inquiry_classifier.py build/data/inquiries.csv build/reports/inquiry-model.json --source-url "https://www.yongin.go.kr/user/bbs/BD_selectBbsList.do?q_bbsCode=1056" --data-kind public-labeled
python experiments/offline_ai/image_classifier.py build/data/tomato-images.csv build/reports/tomato-model.json --source-url "https://www.aihub.or.kr/aihubdata/data/view.do?dataSetSn=150" --data-kind public-labeled
```

## 평가와 모델 사용

1. 지도 분류 모델은 라벨당 독립 출처 그룹 2개 이상이 필요하다. 그룹의 25%를 고정 seed 42로 평가용에 배정하며 각 라벨이 양쪽에 남도록 한다. 2개 그룹만으로 얻은 점수는 신뢰할 만한 현장 성능 추정치가 아니다. 실제 평가는 충분한 수의 독립 그룹이 필요하다.
2. 문의 텍스트의 어휘와 이미지 클래스 중심은 학습 부분에서만 계산한다. 평가용 표본으로 재학습하지 않는다. 이미지별 히스토그램은 각 이미지 자체의 정규화만 사용한다.
3. 결과 JSON에는 데이터 종류, manifest SHA-256, 출처, 학습 및 평가 ID, accuracy, macro F1, 클래스별 precision 및 recall, 혼동 행렬, 다수 클래스 기초 점수가 들어간다. 이미지는 파일별 SHA-256와 실제 Pillow 버전도 기록한다.
4. `inquiry_classifier.predict(model, text)` 및 `image_classifier.predict(model, vector)`로 저장한 모델을 재사용할 수 있다. 토마토 사진의 벡터는 `image_classifier.features(path)`로 구한다. 모델 JSON은 실험 디렉터리에서 생성한 신뢰하는 파일만 읽는다.
5. JSON의 데이터 종류는 사용자가 지정한 선언이며 출처를 자동 인증한 결과가 아니다. 실제 데이터 학습 결과를 공개할 때에는 원본 기준일, 이용 조건, 라벨 검토 방식과 전처리 기록도 함께 검토한다.
6. 문의 모델의 어휘 및 결과 파일에 원문 일부가 들어갈 수 있다. 개인 글과 사진, 실제 모델은 자동으로 커밋하거나 업로드하지 않는다. 공개 전에 비식별 여부와 재배포 권한을 확인한다.

## 현재 정확하게 설명할 수 있는 내용

지역 군집화와 문자 기반 문의 분류, 색상 특성 기반 이미지 분류 알고리즘을 별도 실험으로 구현했다. 학습, 모델 저장, 추론 및 독립 평가 흐름을 합성 입력으로 검사했다. 지역 군집화는 실제 용인시 39개 읍면동 자료로 학습했으며 인구, 세대 및 단일 나이 합계를 검증했다. 운영 서비스에는 연결하지 않았다.

실제 문의 분류 품질, 토마토 병해 판별 성능 및 서비스 연동은 아직 완료하지 않았다. 이 실험을 기존 Gemini 및 ElevenLabs 기능의 학습 모델이라고 소개하지 않는다.

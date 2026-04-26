# DRR Patch Bundles (4 files)

적용 순서:
1) `01-drr-logic.patch`
2) `02-drr-config-wireup.patch`
3) `03-drr-tests.patch`
4) `04-selector-scheduling-instrumentation.patch`

추가된 4번 patch는 `default`, `shuffle`, `drr` 공통 selector 계측을 넣는다.
- `ready_count`
- `read_count`
- `skip_count`
- `ready_to_read_delay`

계측 config:
- `socket.read.scheduling.instrumentation.enable=true`

중요:
- `apply-and-verify.sh`는 패치 적용본 자체가 컴파일/타깃 테스트를 통과하는지 본다.
- `compare-pristine-and-patched.sh`는 순수 Kafka 4.1.0 기준선과 DRR 적용본을 같은 테스트로 비교한다.
- 회귀 판단은 반드시 `compare-pristine-and-patched.sh` 기준으로 한다.

권장 방식:
```bash
/Users/taehee/personal/kafka-project/patches/kafka-4.1.0-drr-bundles/apply-and-verify.sh <kafka-source-root>
```

기준선 비교:
```bash
/Users/taehee/personal/kafka-project/patches/kafka-4.1.0-drr-bundles/compare-pristine-and-patched.sh \
  <pristine-kafka-root> \
  <patched-kafka-root>
```

비교 순서:
1) pristine에서 `comparable suite`를 먼저 실행
2) 같은 suite를 patched에 실행
3) pristine이 통과한 테스트만 patched failure를 regression으로 해석
4) 마지막에 patched 전용 테스트를 별도로 실행

검증 생략:
```bash
/Users/taehee/personal/kafka-project/patches/kafka-4.1.0-drr-bundles/apply-and-verify.sh <kafka-source-root> --no-verify
```

수동 적용:
```bash
PATCH_DIR=/Users/taehee/personal/kafka-project/patches/kafka-4.1.0-drr-bundles
cd <kafka-source-root>
for p in "$PATCH_DIR"/0*.patch; do
  git apply --check --recount "$p" && git apply --recount "$p"
done
```

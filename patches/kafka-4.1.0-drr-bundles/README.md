# DRR Patch Bundles (3 files)

적용 순서:
1) `01-drr-logic.patch`
2) `02-drr-config-wireup.patch`
3) `03-drr-tests.patch`

권장 방식:
```bash
/Users/taehee/personal/kafka-project/patches/kafka-4.1.0-drr-bundles/apply-and-verify.sh <kafka-source-root>
```

검증 생략:
```bash
/Users/taehee/personal/kafka-project/patches/kafka-4.1.0-drr-bundles/apply-and-verify.sh <kafka-source-root> --no-verify
```

수동 적용:
```bash
PATCH_DIR=/Users/taehee/personal/kafka-project/patches/kafka-4.1.0-drr-bundles
cd <kafka-source-root>
for p in "$PATCH_DIR"/0*.patch; do
  git apply --check "$p" && git apply "$p"
done
```

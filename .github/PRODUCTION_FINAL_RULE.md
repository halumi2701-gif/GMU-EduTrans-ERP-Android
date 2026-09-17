# Sales App Production Final Rule

Do not publish, tag, or describe GMU EduTrans Sales as `PRODUCTION FINAL` unless both conditions are true:

1. GitHub Actions workflow `GMU EduTrans Sales Production Gate` completed with conclusion `success` for the exact release commit.
2. Physical-device acceptance checklist in `docs/SALES_PRODUCTION_ACCEPTANCE.md` is completed with PASS.

Unsigned APKs, debug APKs, release candidates, compile-only builds, and backend-only checks are not Production Final.

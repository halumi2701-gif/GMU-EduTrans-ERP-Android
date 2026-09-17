# GMU EduTrans Sales v6.3 — Production Status

Current release phase: **Release Candidate / Production Gate Pending**

The application must not be labelled Production Final until:

- Automated workflow `GMU EduTrans Sales Production Gate` is green for the exact release commit.
- Official GMU signing secrets are configured and the generated APK passes `apksigner verify`.
- Physical Android device acceptance is completed and recorded as PASS in `docs/SALES_RUNTIME_ACCEPTANCE_RECORD.md`.

This file exists to prevent compile success from being confused with production readiness.

# Production Gate Summary

The `GMU EduTrans Sales Production Gate` workflow is the authoritative automated release gate for Sales v6.3.

It blocks release when acceptance credentials or official signing secrets are missing, validates authenticated production backend access, compiles the release APK, signs it with the official GMU key, verifies the signature, and uploads only the verified signed APK as the Production artifact.

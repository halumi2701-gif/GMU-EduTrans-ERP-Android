# Definition of Production Final

GMU EduTrans Sales v6.3 is Production Final only when all of the following are true for the same source commit:

- Sales release source compiles.
- Production backend health check passes.
- Owner Sales-application inbox health check passes.
- APK is signed with the official GMU release key.
- `apksigner verify` passes.
- The GitHub Production Gate concludes `success`.
- Physical Android acceptance concludes PASS.

Anything less remains a Release Candidate.

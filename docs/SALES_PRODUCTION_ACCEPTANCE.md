# GMU EduTrans Sales v6.3 — Production Acceptance

Status `PRODUCTION FINAL` is allowed only when all items below are green.

## Automated CI gate

Workflow: `.github/workflows/sales-production-gate.yml`

Required repository secrets:
- `SALES_ACCEPTANCE_ANON_KEY`
- `SALES_ACCEPTANCE_OWNER_EMAIL`
- `SALES_ACCEPTANCE_OWNER_PASSWORD`
- `SALES_KEYSTORE_B64`
- `SALES_KEYSTORE_PASSWORD`
- `SALES_KEY_ALIAS`
- `SALES_KEY_PASSWORD`

The workflow verifies:
1. Owner acceptance login against production Supabase Auth.
2. Sales portfolio RPC responds successfully.
3. Owner Sales application inbox endpoint responds successfully.
4. Sales release APK compiles.
5. APK is zipaligned, signed with the official GMU keystore, and verified with `apksigner`.
6. Only the verified signed APK is uploaded as `GMU-EduTrans-Sales-v6.3.0-PRODUCTION`.

If any required secret is missing, the production gate intentionally fails. An unsigned APK must never be labelled Production Final.

## One-time device acceptance

Use a non-production test applicant and complete this exact flow on a physical Android device:

1. Open Sales App > Join Sales.
2. Submit test application and record application code.
3. Verify status is PENDING.
4. In ERP Owner > Users > Join Sales, open the same application.
5. Approve / provision the account.
6. Copy the one-time temporary credential securely.
7. Login on the Sales App with the temporary credential.
8. Confirm the app blocks normal workspace until the initial password is changed.
9. Change password.
10. Confirm the Sales Dashboard loads and the account status becomes ACTIVATED.
11. Logout and login again using the new password.
12. Create one test lead, save it, refresh, and confirm it is persisted.
13. Delete/archive the test data according to GMU test-data policy.

Record device model, Android version, app version, timestamp, tester, and PASS/FAIL. Do not store passwords in this document.

## Release rule

Production Final = automated production gate PASS + physical-device acceptance PASS.

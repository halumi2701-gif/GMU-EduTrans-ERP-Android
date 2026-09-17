# GMU EduTrans Sales — Official Android Signing Setup

Configure the following GitHub Actions repository secrets. Do not commit the keystore or passwords to Git.

- `SALES_KEYSTORE_B64`: Base64-encoded official GMU Android keystore file.
- `SALES_KEYSTORE_PASSWORD`: Keystore password.
- `SALES_KEY_ALIAS`: Alias of the Sales release key.
- `SALES_KEY_PASSWORD`: Key password.

Acceptance-test secrets used by the production gate:
- `SALES_ACCEPTANCE_ANON_KEY`: Supabase publishable/anon key used by the app.
- `SALES_ACCEPTANCE_OWNER_EMAIL`: Dedicated Owner acceptance-test account email.
- `SALES_ACCEPTANCE_OWNER_PASSWORD`: Password for that dedicated acceptance-test account.

Security requirements:
- Use a dedicated Android release keystore that is backed up securely outside GitHub.
- Keep the same key for all future updates of this Android package; changing the signing identity prevents normal in-place updates.
- The acceptance Owner account must be dedicated to testing and must not reuse the Owner's personal password.
- Rotate acceptance credentials when staff access changes.
- Never paste signing passwords, private keys, or keystore contents into issue comments, source files, chat transcripts, or build logs.

The workflow `.github/workflows/sales-production-gate.yml` deliberately fails when these secrets are absent. This prevents an unsigned APK from being presented as a production release.

# Sales Acceptance Security Rules

- Never use the personal Owner password as a CI secret.
- Use a dedicated Owner acceptance-test account with only the minimum privileges required for the test.
- Never log temporary Sales passwords.
- Never commit Android keystores, private keys, or passwords.
- Rotate acceptance credentials after staffing or access changes.
- Keep the official Android signing key backed up securely outside the repository.

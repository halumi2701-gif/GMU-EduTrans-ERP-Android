# Sales v6.3 Production Blockers

Only these items may block the `PRODUCTION FINAL` label:

1. Missing or invalid official Android signing secrets.
2. Missing or invalid dedicated production-acceptance credentials.
3. Automated Production Gate failure.
4. Physical Android runtime acceptance failure or not yet executed.

Compile success alone does not clear these blockers.

# Typed Client Generation

This workspace generates typed Angular clients from the contracts in `../contracts`.

## Generator and version pinning

- CLI package: `@openapitools/openapi-generator-cli@2.13.4` (pinned in `package.json`)
- Generator engine: `7.8.0` (pinned in `openapitools.json`)
- Validation note: `validateSpec: false` is set intentionally for both contracts because the generator's 3.1 validator expects `info.license.identifier`, while these contracts are valid without it.

## Contracts and output directories

- `openapi/auth-client.json`: `../contracts/auth-api.yaml` -> `src/generated/auth`
- `openapi/trade-client.json`: `../contracts/trade-api.yaml` -> `src/generated/trade`

## Command

Run client generation from this folder:

```bash
npm run generate:clients
```

Regenerate clients whenever a contract changes and commit generated output in the same change.
Never hand-edit files under `src/generated`.
The generation command keeps the generated `README.md` files, and removes generator support artifacts that are not needed for this story: `.gitignore`, `.openapi-generator-ignore`, `.openapi-generator/`, and `git_push.sh`.

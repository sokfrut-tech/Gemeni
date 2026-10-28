# Jarvis Hive — distributed model resources

## Goal
Use multiple lawful compute pools as one logical model fabric. GitHub is the control plane; GPU workers are execution planes; the Director merges evidence.

## Available resource classes

1. **GitHub larger/self-hosted GPU runners** — suitable for build, emulator and model-worker jobs when the repository/account has access. GitHub documents GPU-powered larger runners and self-hosted runners.
2. **Hugging Face Spaces GPU** — suitable for persistent model endpoints. Hardware ranges from T4/L4/L40S/A100/RTX PRO 6000; paid hardware requires an owned Space and credits or a grant.
3. **Hugging Face ZeroGPU** — suitable for burst inference with dynamic allocation. It is quota based and not a permanent unrestricted GPU pool.
4. **Kaggle GPU notebooks** — useful for ephemeral research/batch jobs; sessions are limited and require an authorized account.
5. **Any authorized cloud GPU** — connected only through environment-provided endpoint/token.

## Policy

- Never scrape, guess, steal or reuse another person's API key.
- Never bypass a provider's authentication, quota, queue or billing controls.
- Workers must expose a health endpoint and model identity.
- Director only accepts workers that return an attestation containing model name, revision, runtime and health status.
- Model downloads are checksummed before use.
- Secrets never enter Git history or artifacts.
- A worker can be revoked without changing Jarvis application code.

## Recommended topology

`Director -> capability router -> worker pool -> independent verifier -> evidence store -> release gate`

The router should prefer a healthy authorized GPU worker, fall back to another provider, and finally use CPU/local inference for small tasks.

## Current limitation

This repository can create the orchestration and adapters, but it cannot provision third-party GPU accounts, accept provider terms, create billing resources, or manufacture credentials. Those resources must be attached through authorized provider accounts/secrets.

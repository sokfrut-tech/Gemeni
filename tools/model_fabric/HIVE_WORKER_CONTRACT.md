# Jarvis Hive — worker contract

Workers are ephemeral, capability-scoped compute nodes. The Director may route a task only when the worker is explicitly configured and its `/health` endpoint responds successfully.

## Accepted worker classes

- GitHub self-hosted GPU runner (`self-hosted`, `linux`, `gpu` labels)
- GitHub larger GPU runner when enabled for the account
- Owned Hugging Face Space/ZeroGPU endpoint
- Owned cloud GPU endpoint
- Owned local Ollama/vLLM endpoint

GitHub Actions supports routing jobs to self-hosted runners by labels and supports GPU larger runners on eligible plans. Hugging Face ZeroGPU dynamically allocates and releases shared GPU capacity; quota and availability are provider-controlled.

## Security rules

1. No credential harvesting.
2. No leaked/stolen API keys.
3. No bypass of provider quotas or authentication.
4. Credentials come only from environment/GitHub Secrets at execution time.
5. Worker responses are treated as untrusted evidence.
6. A worker cannot approve its own release.
7. Every job records worker name, model, timestamp, health result, and output hash.
8. Workers are revocable and may be disabled without changing application code.

## Routing

`capability_router.py` selects a healthy configured worker. If none is available it returns `no_worker`; it does not silently claim that a local or cloud model exists.

## Required capabilities

`reasoning`, `code_review`, `research`, `android`, `ndk`, `speech`, `qa`, `security`.

A worker may advertise only capabilities explicitly assigned by the Director configuration.

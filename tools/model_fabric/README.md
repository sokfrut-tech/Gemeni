# Jarvis Model Fabric

Цель: реальный multi-model backend для инженерной фабрики Jarvis. Модели не встраиваются в APK. Они работают как отдельные workers и общаются с Director через OpenAI-compatible API.

## Поддерживаемые режимы

1. Self-hosted Qwen/DeepSeek через vLLM.
2. Любой совместимый OpenAI API endpoint.
3. Несколько workers одновременно: planner, coder, reviewer, Android/ColorOS, speech, NDK/GLES, QA.

vLLM предоставляет `/v1/chat/completions`, `/v1/responses`, `/v1/models` и health endpoint; используем только authenticated `/v1` paths и reverse proxy для остальных endpoints. См. официальную документацию vLLM.

Qwen-Agent используется как optional agent framework для MCP/tool calling; его MCP-интеграция позволяет давать модели filesystem, memory и другие инструменты.

## Безопасность

Никаких чужих API keys, ключей из утечек или обхода авторизации. Для облачных моделей используются только секреты владельца. Для self-hosted worker ключ может быть локальным токеном vLLM.

## Конвейер

Director -> model-router -> parallel specialists -> evidence -> verifier -> release gate.

Каждый worker обязан вернуть:
- conclusion;
- changed files;
- tests executed;
- evidence paths;
- unresolved risks.

Нельзя выдавать `READY`, если отсутствует runtime evidence.

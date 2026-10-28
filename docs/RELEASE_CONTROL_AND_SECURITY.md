# Команда технического контроля и безопасности

Финальный выпуск Jarvis/Gemeni проходит независимую команду контроля. Она не разрабатывает исправления и не считается доказательством сама для себя.

## Состав

- Release QA Lead — собирает результаты всех независимых тестов.
- Android Technical Controller — проверяет APK, manifest, services, permissions и lifecycle.
- Runtime Controller — подтверждает работу на эмуляторе/устройстве реальными действиями.
- Native/ABI Controller — проверяет JNI, `.so`, ABI и native crashes.
- Speech Controller — проверяет audio capture, VAD/KWS/ASR/TTS.
- Functional Controller — проверяет Intent, поиск, навигацию и системные действия.
- Security Controller — проверяет secrets, permissions, exported components, dangerous configuration и критические уязвимости.
- Evidence Auditor — проверяет, что каждый PASS имеет воспроизводимое evidence.

## Правило выпуска

`PASS` возможен только если build, runtime, functional, security, manifest, ABI и artifact имеют PASS; независимый QA дал evidence; critical/high security findings отсутствуют.

Отсутствующее evidence = BLOCK.

Нельзя получить PASS отключением, ослаблением или удалением теста.

Любой BLOCK возвращает дефект в ремонтный цех и не останавливает остальные независимые тестовые ячейки.

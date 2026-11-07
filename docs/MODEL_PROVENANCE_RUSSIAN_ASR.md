# Russian ASR model provenance

**Product:** Jarvis Android  
**Purpose:** local Russian speech recognition (ASR), not the conversational language model.  
**Upstream:** Alpha Cephei, `alphacep/vosk-model-small-streaming-ru`  
**Declared upstream license:** Apache License 2.0  
**Pinned upstream revision prefix:** `5fbd908cb21cbd585fa21461f463ba41fbcbcb68`  
**Download:** approximately 29 MB for the selected quantized streaming subset.

## Selected files

Only the following files are downloaded into the app-private model directory:

| Local file | Upstream path | Approx. size | SHA-256 |
|---|---|---:|---|
| `encoder.int8.onnx` | `am-onnx/encoder.int8.onnx` | 26.2 MB | `e0db705e94ec35d803b1df4f40cda23d064e1142977c80ab288430b109777a9d` |
| `decoder.onnx` | `am-onnx/decoder.onnx` | 2.09 MB | `89b3088a9e20e1ef7f2e85ce1a3478afe6a9c4ac57369cabcc4beb8e95328ea0` |
| `joiner.int8.onnx` | `am-onnx/joiner.int8.onnx` | 259 kB | `b55784b071ab7512eab4c7c44e4f5478284ef33c83562cc6a249b972515a31e5` |
| `tokens.txt` | `lang/tokens.txt` | 6.39 kB | Not yet pinned |

The three ONNX SHA-256 values are published by the official upstream file metadata. The token file is fetched from the pinned upstream revision and bounded by a 4 MiB limit, but its SHA-256 has not yet been established. **This is an open integrity item and must be closed before a production release.** Do not invent a digest or treat the token file as cryptographically verified.

## License and attribution

The official Alpha Cephei Hugging Face model card declares `apache-2.0`. Use the official repository as the download source; do not switch back to a re-published mirror with no declared license without obtaining independent licensing confirmation.

- Model repository: https://huggingface.co/alphacep/vosk-model-small-streaming-ru
- Model card README: https://huggingface.co/alphacep/vosk-model-small-streaming-ru/blob/main/README.md
- Apache-2.0 license text: https://www.apache.org/licenses/LICENSE-2.0

This model is not bundled in the APK. It is downloaded only after the user explicitly approves the download, stored in app-private storage, and checked before Sherpa-ONNX loads it.

## Release checklist

- [x] Pin the full immutable upstream commit SHA in the source manifest.
- [ ] Obtain and pin the SHA-256 of `lang/tokens.txt` from the same immutable revision.
- [ ] Confirm all downloaded files are from the official Apache-2.0 upstream repository.
- [ ] Verify real redirect hosts remain within the explicit allowlist.
- [ ] Pass exact-head downloader unit tests, CodeQL, APK build and emulator tests.
- [ ] Test download, checksum verification, ASR initialization and offline recognition on OPPO Find X6 Pro.
- [ ] Include Apache-2.0 attribution and required notices in the app's third-party notices.


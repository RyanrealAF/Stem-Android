# StemFlow Android

Native Android audio-to-MIDI instrument.

Pipeline:
Audio → validation → neural six-stem separation → sequential stem transcription → MIDI validation → export.

Required stems: vocals, drums, bass, guitar, piano, other.

The UI is intentionally minimal. Long-running processing uses a native media-processing foreground service. The engine is designed to process stems sequentially and avoid retaining decoded PCM for all stems simultaneously.

This foundation contains no synthetic separation and no fabricated accuracy scores. Real neural separation and transcription engines are the next implementation layer.

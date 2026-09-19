# Basic Pitch Android inference contract

Spotify's Basic Pitch accepts arbitrary input sample rates and resamples to 22,050 Hz before inference. It is polyphonic and produces note, onset, and contour outputs, then a decoding stage converts those outputs into note events and MIDI.

StemFlow will mirror that contract natively:
1. Read one file-backed stem at a time.
2. Downmix to mono.
3. Resample to 22,050 Hz.
4. Window the audio without loading the complete stem into RAM.
5. Run the published Basic Pitch ONNX model through Android ONNX Runtime.
6. Decode note/onset/contour tensors using the published thresholds and note-creation logic.
7. Preserve pitch bends where available.
8. Write MIDI directly to disk.

The implementation deliberately does not invent a MIDI result until the feature extraction and tensor decoding match the published model contract.

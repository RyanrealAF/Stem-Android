# StemFlow separator runtime

The Android app treats neural separation as a model-runtime boundary.

The target six-source model is HTDemucs 6s. The upstream Demucs project identifies it as an experimental six-source model producing drums, bass, other, vocals, guitar, and piano, and notes that the piano output has more bleeding/artifacts than the other sources. citeturn0search3

A current ONNX export exists for HTDemucs 6s and is reported as a single 258 MB FP32 model or 136 MB FP16-weight model. Its published parity test reports a maximum absolute difference of 2.42e-4 against PyTorch FP32 on its test input. citeturn0search2

StemFlow does not bundle that model until the Android runtime is validated on-device. The next implementation target is an ONNX Runtime Mobile adapter using the exported model, followed by numerical parity tests and a real-device benchmark.

No synthetic separator is permitted as a fallback.

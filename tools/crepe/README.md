# CREPE conversion

Converts official pretrained CREPE weights (any capacity) into builtin-only
TFLite models with parity checks. Source weights are deliberately kept outside
Git; URLs, sizes and SHA-256 hashes are recorded in `convert_crepe.py` so any
run is reproducible. Use Python 3.11:

```bash
python3.11 -m venv tools/crepe/.venv
tools/crepe/.venv/bin/pip install -r tools/crepe/requirements.txt
tools/crepe/.venv/bin/python tools/crepe/convert_crepe.py \
  ~/Downloads/crepe/model-small.h5.bz2 tools/crepe/output --capacity small
tools/crepe/.venv/bin/python tools/crepe/convert_crepe.py \
  ~/Downloads/crepe/model-full.h5.bz2 tools/crepe/output --capacity full
```

Capacities: tiny | small | medium | large | full (tiny = 986 KB fp16,
small = 3.3 MB, full = 44.5 MB). Only the `{capacity}_crepe_fp16.tflite` plus
its generated manifest are candidates for packaging after source, structure,
conversion parity, and device tests pass. A tiny run must reproduce the
committed `app/src/tinyCrepe/assets/tiny_crepe_fp16.tflite` byte-for-byte
(fp16 sha256 5f861cc7...).

## Selecting the model at build time

`-PcrepeModel=tiny|small|medium|large|full` picks which bundled model and
asset directory are used; it defaults to `tiny` (zero-change normal build).
The app reads the selected asset name from `BuildConfig.CREPE_MODEL_ASSET`.

```bash
GRADLE_USER_HOME="$PWD/.gradle-home" ./gradlew -PtinyCrepeEnabled=true -PcrepeModel=small assembleDebug
./install-device.sh DEVICE_SERIAL --tiny-crepe   # keeps the last built APK
adb -s DEVICE_SERIAL shell setprop log.tag.Tuner D
adb -s DEVICE_SERIAL logcat -s Tuner:D TinyCrepe:D '*:S'
```

The app offers DSP-only, hybrid conflict arbitration, and CREPE-primary modes.
The experiment build is arm64-only; the normal build keeps all ABIs.

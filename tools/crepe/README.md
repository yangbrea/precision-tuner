# Tiny CREPE conversion

The source weights are deliberately kept outside Git. Use Python 3.11:

```bash
python3.11 -m venv tools/crepe/.venv
tools/crepe/.venv/bin/pip install -r tools/crepe/requirements.txt
tools/crepe/.venv/bin/python tools/crepe/convert_tiny_crepe.py \
  ~/Downloads/tiny-crepe/model-tiny.h5.bz2 tools/crepe/output
```

Only `tiny_crepe_fp16.tflite` and its generated manifest are candidates for
packaging after source, structure, conversion parity, and device tests pass.

The normal build excludes the model and native runtime. Build or install the
arm64 Tiny CREPE experiment explicitly:

```bash
GRADLE_USER_HOME="$PWD/.gradle-home" ./gradlew -PtinyCrepeEnabled=true assembleDebug
./install-device.sh DEVICE_SERIAL --tiny-crepe
adb -s DEVICE_SERIAL shell setprop log.tag.Tuner D
adb -s DEVICE_SERIAL logcat -s Tuner:D TinyCrepe:D '*:S'
```

The app offers DSP-only, hybrid conflict arbitration, and CREPE-primary modes.
The experiment build is arm64-only; the normal build keeps all ABIs.

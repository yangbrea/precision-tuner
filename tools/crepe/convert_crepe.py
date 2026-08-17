#!/usr/bin/env python3
"""Validate official CREPE weights (any capacity) and emit builtin-only TFLite models."""

import argparse
import bz2
import hashlib
import json
from pathlib import Path

import h5py
import numpy as np
import tensorflow as tf

# Official pretrained weights (marl/crepe), deliberately kept outside Git; the
# recorded URLs/sizes/hashes make the conversion reproducible. The tiny entry
# mirrors the original convert_tiny_crepe.py; small/full were captured once
# from the official model-{capacity}.h5.bz2 files.
SOURCES = {
    "tiny": {
        "url": "https://raw.githubusercontent.com/marl/crepe/models/model-tiny.h5.bz2",
        "compressed_sha256": "9c1d76d405674d2327f5636347d07f551dfd11c6acc0475b7fd85082cdab0add",
        "compressed_size": 1_307_310,
        "weights_sha256": "409254d6a537cc432061112b7ec5f6fd283658f108fc2abc24db9c25858b42df",
        "weights_size": 2_011_144,
    },
    "small": {
        "url": "https://raw.githubusercontent.com/marl/crepe/models/model-small.h5.bz2",
        "compressed_sha256": "90a6c62c57d35a275df69344dcf510c56110c267465c86816e3d1ee880eefba5",
        "compressed_size": 4_320_088,
        "weights_sha256": "67cff9f4bf1f3b63496fdcb631b830f994f2904705401cd208d2b86a0d16654a",
        "weights_size": 6_579_224,
    },
    "full": {
        "url": "https://raw.githubusercontent.com/marl/crepe/models/model-full.h5.bz2",
        "compressed_sha256": "b27a576b96aea1545f10ca2aded114f0fbd6df8dcebb669236fb59f6065f01d8",
        "compressed_size": 57_689_301,
        "weights_sha256": "b6fd2758b03a8625a16fe86cd474ff0d8f30ad9a05e4bee2244e13e98664f860",
        "weights_size": 89_038_936,
    },
}

CAPACITY_MULTIPLIER = {"tiny": 32, "small": 64, "medium": 128, "large": 256, "full": 256}


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def build_model(capacity: str) -> tf.keras.Model:
    multiplier = CAPACITY_MULTIPLIER[capacity]
    # Official crepe.py filter pattern: [4, .5, .5, .5, 1, 2] * capacity.
    filters = [int(n * multiplier) for n in [4, 0.5, 0.5, 0.5, 1, 2]]
    inputs = tf.keras.Input(shape=(1024,), name="input", dtype=tf.float32)
    x = tf.keras.layers.Reshape((1024, 1, 1), name="input-reshape")(inputs)
    widths = [512, 64, 64, 64, 64, 64]
    strides = [(4, 1), (1, 1), (1, 1), (1, 1), (1, 1), (1, 1)]
    for index, (count, width, stride) in enumerate(zip(filters, widths, strides), 1):
        x = tf.keras.layers.Conv2D(
            count, (width, 1), strides=stride, padding="same", activation="relu", name=f"conv{index}"
        )(x)
        x = tf.keras.layers.BatchNormalization(name=f"conv{index}-BN")(x)
        x = tf.keras.layers.MaxPool2D((2, 1), padding="valid", name=f"conv{index}-maxpool")(x)
        x = tf.keras.layers.Dropout(0.25, name=f"conv{index}-dropout")(x)
    x = tf.keras.layers.Permute((2, 1, 3), name="transpose")(x)
    x = tf.keras.layers.Flatten(name="flatten")(x)
    outputs = tf.keras.layers.Dense(360, activation="sigmoid", name="classifier")(x)
    return tf.keras.Model(inputs, outputs)


def convert(model: tf.keras.Model, fp16: bool) -> bytes:
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS]
    if fp16:
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        converter.target_spec.supported_types = [tf.float16]
    return converter.convert()


def infer_tflite(model_bytes: bytes, samples: np.ndarray) -> np.ndarray:
    interpreter = tf.lite.Interpreter(model_content=model_bytes, num_threads=1)
    interpreter.allocate_tensors()
    input_info = interpreter.get_input_details()[0]
    output_info = interpreter.get_output_details()[0]
    if tuple(input_info["shape"]) != (1, 1024) or tuple(output_info["shape"]) != (1, 360):
        raise ValueError(f"unexpected tensors: {input_info['shape']} -> {output_info['shape']}")
    interpreter.set_tensor(input_info["index"], samples.astype(np.float32))
    interpreter.invoke()
    return interpreter.get_tensor(output_info["index"])


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("weights", type=Path, help="official model-{capacity}.h5.bz2")
    parser.add_argument("output", type=Path)
    parser.add_argument(
        "--capacity", choices=CAPACITY_MULTIPLIER.keys(), default="tiny",
        help="CREPE capacity variant (tiny/small/medium/large/full)",
    )
    args = parser.parse_args()
    source = SOURCES[args.capacity]

    compressed = args.weights.read_bytes()
    if len(compressed) != source["compressed_size"] or sha256(compressed) != source["compressed_sha256"]:
        raise ValueError(f"compressed CREPE {args.capacity} weights failed size/SHA-256 validation")
    h5_bytes = bz2.decompress(compressed)
    if len(h5_bytes) != source["weights_size"] or sha256(h5_bytes) != source["weights_sha256"]:
        raise ValueError(f"decompressed CREPE {args.capacity} weights failed size/SHA-256 validation")

    args.output.mkdir(parents=True, exist_ok=True)
    h5_path = args.output / f"model-{args.capacity}.h5"
    h5_path.write_bytes(h5_bytes)
    with h5py.File(h5_path, "r") as weights:
        if not any("conv1" in key for key in weights.keys()):
            raise ValueError("official CREPE layer structure not found")

    model = build_model(args.capacity)
    model.load_weights(h5_path)
    fp32 = convert(model, fp16=False)
    fp16 = convert(model, fp16=True)

    rng = np.random.default_rng(136)
    fixtures = [np.zeros((1, 1024), np.float32), rng.normal(size=(1, 1024)).astype(np.float32)]
    max_fp32_error = 0.0
    max_fp16_error = 0.0
    for fixture in fixtures:
        keras = model(fixture, training=False).numpy()
        max_fp32_error = max(max_fp32_error, float(np.max(np.abs(keras - infer_tflite(fp32, fixture)))))
        max_fp16_error = max(max_fp16_error, float(np.max(np.abs(keras - infer_tflite(fp16, fixture)))))
    if max_fp32_error > 1e-4 or max_fp16_error > 2e-2:
        raise ValueError(f"conversion parity failed: fp32={max_fp32_error}, fp16={max_fp16_error}")

    stem = f"{args.capacity}_crepe"
    fp32_path = args.output / f"{stem}_fp32.tflite"
    fp16_path = args.output / f"{stem}_fp16.tflite"
    fp32_path.write_bytes(fp32)
    fp16_path.write_bytes(fp16)
    h5_path.unlink()
    manifest = {
        "source_url": source["url"],
        "source_sha256": source["compressed_sha256"],
        "weights_sha256": source["weights_sha256"],
        "tensorflow": tf.__version__,
        "capacity": args.capacity,
        "filters": [int(n * CAPACITY_MULTIPLIER[args.capacity]) for n in [4, 0.5, 0.5, 0.5, 1, 2]],
        "input": [1, 1024],
        "output": [1, 360],
        "fp32_sha256": sha256(fp32),
        "fp16_sha256": sha256(fp16),
        "fp32_max_abs_error": max_fp32_error,
        "fp16_max_abs_error": max_fp16_error,
    }
    (args.output / f"{stem}_manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(json.dumps(manifest, indent=2))


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Validate official Tiny CREPE weights and emit builtin-only TFLite models."""

import argparse
import bz2
import hashlib
import json
from pathlib import Path

import h5py
import numpy as np
import tensorflow as tf

COMPRESSED_SHA256 = "9c1d76d405674d2327f5636347d07f551dfd11c6acc0475b7fd85082cdab0add"
H5_SHA256 = "409254d6a537cc432061112b7ec5f6fd283658f108fc2abc24db9c25858b42df"
SOURCE_URL = "https://raw.githubusercontent.com/marl/crepe/models/model-tiny.h5.bz2"


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def build_model() -> tf.keras.Model:
    inputs = tf.keras.Input(shape=(1024,), name="input", dtype=tf.float32)
    x = tf.keras.layers.Reshape((1024, 1, 1), name="input-reshape")(inputs)
    filters = [128, 16, 16, 16, 32, 64]
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
    parser.add_argument("weights", type=Path, help="official model-tiny.h5.bz2")
    parser.add_argument("output", type=Path)
    args = parser.parse_args()

    compressed = args.weights.read_bytes()
    if len(compressed) != 1_307_310 or sha256(compressed) != COMPRESSED_SHA256:
        raise ValueError("compressed Tiny CREPE weights failed size/SHA-256 validation")
    h5_bytes = bz2.decompress(compressed)
    if len(h5_bytes) != 2_011_144 or sha256(h5_bytes) != H5_SHA256:
        raise ValueError("decompressed Tiny CREPE weights failed size/SHA-256 validation")

    args.output.mkdir(parents=True, exist_ok=True)
    h5_path = args.output / "model-tiny.h5"
    h5_path.write_bytes(h5_bytes)
    with h5py.File(h5_path, "r") as weights:
        if not any("conv1" in key for key in weights.keys()):
            raise ValueError("official CREPE layer structure not found")

    model = build_model()
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

    fp32_path = args.output / "tiny_crepe_fp32.tflite"
    fp16_path = args.output / "tiny_crepe_fp16.tflite"
    fp32_path.write_bytes(fp32)
    fp16_path.write_bytes(fp16)
    h5_path.unlink()
    manifest = {
        "source_url": SOURCE_URL,
        "source_sha256": COMPRESSED_SHA256,
        "weights_sha256": H5_SHA256,
        "tensorflow": tf.__version__,
        "input": [1, 1024],
        "output": [1, 360],
        "fp32_sha256": sha256(fp32),
        "fp16_sha256": sha256(fp16),
        "fp32_max_abs_error": max_fp32_error,
        "fp16_max_abs_error": max_fp16_error,
    }
    (args.output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(json.dumps(manifest, indent=2))


if __name__ == "__main__":
    main()

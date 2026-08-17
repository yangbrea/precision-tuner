#!/usr/bin/env python3
"""设计调音成功提示音(Cue Sound)。

合成模型与 App 内 ClickSound.generateDing 完全一致:
  多个倍频正弦 × 指数衰减包络,44100 Hz / 16 bit / 单声道 WAV。

用法:
  python3 tools/cue_designer.py                     # 播放当前 App 提示音(基准)
  python3 tools/cue_designer.py --compare           # 先播当前音,再播你的候选
  python3 tools/cue_designer.py --presets           # 依次播放 6 个预置候选
  python3 tools/cue_designer.py --fund 1568 --partials "1:1.0 2:0.3" --dur 200 --decay 120 --amp 0.4
  python3 tools/cue_designer.py --out /tmp/ding     # 只导出 WAV,不播放

频率组分(--partials):空格分隔的 "倍频:增益" 对,倍频是相对基频的倍数,
可以是非整数(钟/铃声的不谐泛音),增益是相对强度(自动归一化)。
"""

import argparse
import math
import shutil
import struct
import subprocess
import wave
from pathlib import Path

SAMPLE_RATE = 44100

# ---------------------------------------------------------------------------
# 在这里直接改也行:当前 App 提示音的参数(与 CueSoundPlayer 一致)
# ---------------------------------------------------------------------------
CURRENT = {
    "name": "current (App 现在的声音)",
    "fundamental_hz": 2093.0,   # C7
    "partials": [(1, 1.00), (2, 0.25), (3, 0.15), (5, 0.08)],
    "duration_ms": 140.0,
    "decay": 200.0,             # 指数衰减率:越大越短促
    "amplitude": 0.4,
}

# ---------------------------------------------------------------------------
# 预置候选(供 --presets 试听挑选)
# ---------------------------------------------------------------------------
PRESETS = [
    CURRENT,
    {
        "name": "soft bell (柔和钟声,不谐泛音)",
        "fundamental_hz": 1318.5,   # E6
        "partials": [(1, 1.00), (2.76, 0.30), (5.40, 0.10)],
        "duration_ms": 300.0,
        "decay": 120.0,
        "amplitude": 0.35,
    },
    {
        "name": "two-tone ping (双音短促)",
        "fundamental_hz": 880.0,    # A5
        "partials": [(1, 1.00), (2, 0.50)],
        "duration_ms": 120.0,
        "decay": 250.0,
        "amplitude": 0.4,
    },
    {
        "name": "major chime (大三和弦风铃)",
        "fundamental_hz": 1046.5,   # C6
        "partials": [(1, 1.00), (1.25, 0.40), (1.5, 0.30)],
        "duration_ms": 240.0,
        "decay": 150.0,
        "amplitude": 0.35,
    },
    {
        "name": "low boop (低频圆润)",
        "fundamental_hz": 440.0,    # A4
        "partials": [(1, 1.00), (2, 0.20)],
        "duration_ms": 180.0,
        "decay": 100.0,
        "amplitude": 0.5,
    },
    {
        "name": "sharp click (高频短促)",
        "fundamental_hz": 3000.0,
        "partials": [(1, 1.00)],
        "duration_ms": 60.0,
        "decay": 400.0,
        "amplitude": 0.5,
    },
]


def synth(spec):
    """合成一段声音,返回 0..1 浮点采样列表。"""
    n = max(1, int(spec["duration_ms"] / 1000.0 * SAMPLE_RATE))
    total_gain = sum(g for _, g in spec["partials"])
    samples = []
    for i in range(n):
        t = i / SAMPLE_RATE
        env = math.exp(-t * spec["decay"])
        wave_sum = sum(
            g * math.sin(2.0 * math.pi * spec["fundamental_hz"] * mult * t)
            for mult, g in spec["partials"]
        ) / total_gain
        samples.append(spec["amplitude"] * env * wave_sum)
    return samples


def write_wav(path, samples):
    peak = max(1e-9, max(abs(s) for s in samples))
    scale = 32767.0 * 0.95 / peak
    frames = bytearray()
    for s in samples:
        v = int(max(-1.0, min(1.0, s * scale)) * 32767.0)
        frames += struct.pack("<h", v)
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SAMPLE_RATE)
        w.writeframes(bytes(frames))


def play(path):
    for cmd in (["ffplay", "-nodisp", "-autoexit", str(path)],
                ["aplay", "-q", str(path)],
                ["paplay", str(path)]):
        if shutil.which(cmd[0]):
            subprocess.run(cmd, check=False)
            return
    print(f"  未找到 ffplay/aplay/paplay,请手动打开: {path}")


def describe(spec):
    partials = " ".join(f"{m:g}:{g:g}" for m, g in spec["partials"])
    return (f"fund={spec['fundamental_hz']:g}Hz dur={spec['duration_ms']:g}ms "
            f"decay={spec['decay']:g} amp={spec['amplitude']:g} partials=[{partials}]")


def parse_partials(text):
    out = []
    for token in text.replace(",", " ").split():
        if ":" not in token:
            raise SystemExit(f"无法解析组分 '{token}',应为 倍频:增益,如 2:0.25")
        mult_s, gain_s = token.split(":")
        out.append((float(mult_s), float(gain_s)))
    if not out:
        raise SystemExit("--partials 不能为空")
    return out


def main():
    parser = argparse.ArgumentParser(description="调音成功提示音设计器")
    parser.add_argument("--fund", type=float, help="基频 Hz(默认 2093)")
    parser.add_argument("--partials", help='倍频组分,如 "1:1.0 2:0.25 3:0.15 5:0.08"')
    parser.add_argument("--dur", type=float, help="时长 ms(默认 140)")
    parser.add_argument("--decay", type=float, help="衰减率(默认 200)")
    parser.add_argument("--amp", type=float, help="幅度 0..1(默认 0.4)")
    parser.add_argument("--compare", action="store_true", help="先播当前音再播候选")
    parser.add_argument("--presets", action="store_true", help="依次播放预置候选")
    parser.add_argument("--out", metavar="DIR", help="只导出 WAV 到目录(不播放)")
    parser.add_argument("--play", action="store_true", help="配合 --out 使用:导出后也播放")
    args = parser.parse_args()

    out_dir = Path(args.out) if args.out else None
    if out_dir:
        out_dir.mkdir(parents=True, exist_ok=True)

    def run_one(spec, filename=None, play_it=True):
        print(f"\n▶ {spec['name']}")
        print(f"  {describe(spec)}")
        target = out_dir / filename if (filename and out_dir) else None
        if target is None:
            target = Path("/tmp") / f"cue_{int(spec['fundamental_hz'])}_{spec['duration_ms']:.0f}ms.wav"
        write_wav(target, synth(spec))
        print(f"  wav: {target}")
        if play_it:
            play(target)
        return target

    if args.presets:
        for spec in PRESETS:
            run_one(spec, filename=f"preset_{PRESETS.index(spec)}.wav", play_it=args.play)
        print("\n试听完告诉我喜欢的编号/参数,我会移植进 App。")
        return

    # 组装候选参数(未指定的沿用当前音)
    candidate = dict(CURRENT)
    if args.fund is not None:
        candidate["fundamental_hz"] = args.fund
    if args.partials is not None:
        candidate["partials"] = parse_partials(args.partials)
    if args.dur is not None:
        candidate["duration_ms"] = args.dur
    if args.decay is not None:
        candidate["decay"] = args.decay
    if args.amp is not None:
        candidate["amplitude"] = args.amp
    candidate["name"] = "candidate (你的候选)"

    if args.compare:
        print("先听当前音:")
        current_path = run_one(CURRENT, filename="current.wav", play_it=args.play or not out_dir)
        print("\n再听你的候选:")
    run_one(candidate, filename="candidate.wav", play_it=args.play or not out_dir)

    if not args.compare and not out_dir:
        print("\n满意的话,把这行参数发给我,我移植进 App:"
              f"\n  fund={candidate['fundamental_hz']:g} dur={candidate['duration_ms']:g} "
              f"decay={candidate['decay']:g} amp={candidate['amplitude']:g} "
              f"partials={candidate['partials']}")


if __name__ == "__main__":
    main()

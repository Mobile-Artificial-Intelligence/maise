"""
extract_kitten_voices.py
------------------------
Extracts individual voice style embeddings from the KittenTTS voices.npz file
and writes them as little-endian float32 binary files ready to be placed in
the Android app's assets/voices/ directory.

Usage:
    python extract_kitten_voices.py --npz voices.npz --out <path-to-assets>/voices/

Download voices.npz from:
    https://huggingface.co/KittenML/kitten-tts-mini-0.8/resolve/main/voices.npz
"""

import argparse
import os
import numpy as np

# Maps friendly voice name → internal NPZ key (from config.json voice_aliases)
VOICE_ALIASES = {
    "bella":  "expr-voice-2-f",
    "jasper": "expr-voice-2-m",
    "luna":   "expr-voice-3-f",
    "bruno":  "expr-voice-3-m",
    "rosie":  "expr-voice-4-f",
    "hugo":   "expr-voice-4-m",
    "kiki":   "expr-voice-5-f",
    "leo":    "expr-voice-5-m",
}


def extract(npz_path: str, out_dir: str) -> None:
    os.makedirs(out_dir, exist_ok=True)
    voices = np.load(npz_path)
    available = set(voices.files)

    print(f"NPZ keys found: {sorted(available)}\n")

    for name, alias in VOICE_ALIASES.items():
        if alias not in available:
            print(f"[SKIP] '{alias}' not found in NPZ for voice '{name}'")
            continue

        data = voices[alias].astype("<f4")  # ensure little-endian float32
        out_path = os.path.join(out_dir, f"kitten-{name}.bin")
        data.tofile(out_path)
        size_kb = os.path.getsize(out_path) / 1024
        print(f"[OK]   {alias} → kitten-{name}.bin  shape={data.shape}  {size_kb:.1f} KB")

    print("\nDone. Copy the kitten-*.bin files into assets/voices/")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Extract KittenTTS voice embeddings")
    parser.add_argument("--npz", required=True, help="Path to voices.npz")
    parser.add_argument(
        "--out",
        default=os.path.join(
            os.path.dirname(__file__),
            "..", "app", "src", "main", "assets", "voices",
        ),
        help="Output directory (default: app/src/main/assets/voices/)",
    )
    args = parser.parse_args()
    extract(args.npz, os.path.abspath(args.out))

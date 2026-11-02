#!/usr/bin/env python3
"""Offline verifier / reproducer for the webOS CBCS HD fix.

Run this against a copy of the Widevine modular decryptor that you extracted
from YOUR OWN rooted TV. It does NOT ship any LG binary. It:

  1. identifies the file by SHA256 against the known-firmware table,
  2. confirms the 28-byte region at the patch offset matches the known original,
  3. applies the 28-byte correction in memory,
  4. confirms the result's SHA256 equals the known patched hash,

and, with --write OUT, writes the corrected copy next to you for inspection.
This mirrors exactly what cbcs-hd-fix.sh does on the TV.

Usage:
  python3 verify.py /path/to/libwidevine-modular_decryptor.so
  python3 verify.py /path/to/lib.so --write /path/to/patched.so
"""
import argparse
import hashlib
import sys

# Known-firmware table. Keep in sync with cbcs-hd-fix.sh and KNOWN_FIRMWARES.md.
# Each entry: original SHA256 -> correction record.
#   size   : expected library size in bytes
#   offset : file offset of the 28-byte block (0x1370 == 4976 on the C9)
#   old    : the 28 original bytes at that offset (identification only)
#   new    : the 28 corrected bytes (our authored replacement instructions)
#   label  : human-readable TV / firmware description
KNOWN = {
    "d35a1b16ab053305773a072540d1ab9dfd9d23d61d41dc48b552c2fe4bc0112c": {
        "patched": "979d468696825745f24b844991eec575ce724542af9f902e50599b1baf2db5df",
        "size": 8260,
        "offset": 0x1370,
        "old": "fff7e0ed3b49a9230d9a7944fff794ed079a5c4655f8083c1a440792",
        "new": "079a5c4655f8083c1a44d9f81000012803d1d9f8140000b100220792",
        "label": "LG OLED C9 (OLED55C9PSA) fw 05.50.00 / webOS 4.5 / Widevine 14",
    },
}


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def main() -> int:
    ap = argparse.ArgumentParser(description="Offline verifier for the webOS CBCS HD fix")
    ap.add_argument("library", help="path to a decryptor .so extracted from your own TV")
    ap.add_argument("--write", metavar="OUT", help="write the corrected copy to OUT")
    args = ap.parse_args()

    with open(args.library, "rb") as fh:
        data = fh.read()

    digest = sha256(data)
    print(f"input SHA256 : {digest}")
    print(f"input size   : {len(data)} bytes")

    if digest in {e["patched"] for e in KNOWN.values()}:
        print("result       : this file is ALREADY a known patched build. Nothing to do.")
        return 0

    rec = KNOWN.get(digest)
    if rec is None:
        print("result       : NOT RECOGNIZED.")
        print("This library is not in the known-firmware table, so it will not be")
        print("patched. Please report the SHA256 above with your TV model, firmware,")
        print("webOS and Widevine versions so support can be added safely.")
        return 3

    print(f"recognized   : {rec['label']}")

    if len(data) != rec["size"]:
        print(f"ERROR        : size {len(data)} != expected {rec['size']}")
        return 1

    off = rec["offset"]
    old = bytes.fromhex(rec["old"])
    new = bytes.fromhex(rec["new"])
    if len(old) != 28 or len(new) != 28:
        print("ERROR        : table patch block is not 28 bytes")
        return 1

    region = data[off:off + 28]
    if region != old:
        print(f"ERROR        : bytes at 0x{off:x} do not match the known original region")
        print(f"               found    {region.hex()}")
        print(f"               expected {old.hex()}")
        return 1
    print(f"region 0x{off:x} : matches known original ({old.hex()})")

    patched = bytearray(data)
    patched[off:off + 28] = new
    patched = bytes(patched)
    out_digest = sha256(patched)
    print(f"patched SHA256: {out_digest}")

    if out_digest != rec["patched"]:
        print(f"ERROR        : patched hash != expected {rec['patched']}")
        return 1

    changed = sum(1 for a, b in zip(data, patched) if a != b)
    print(f"result       : OK - reproduces the known patched hash ({changed} bytes changed).")

    if args.write:
        try:
            with open(args.write, "xb") as fh:
                fh.write(patched)
        except FileExistsError:
            print(f"ERROR        : output {args.write} already exists; refusing to overwrite")
            return 1
        print(f"wrote        : {args.write}")

    return 0


if __name__ == "__main__":
    sys.exit(main())

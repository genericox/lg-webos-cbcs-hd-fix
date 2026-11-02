# Reproducing the 28-byte patch

The corrected bytes were derived reproducibly with [Ghidra](https://ghidra-sre.org/),
not hand-assembled. This directory documents how, so the patch can be audited
and re-derived without trusting a prebuilt binary.

## Inputs (never redistributed here)

- The original `/usr/lib/svp/libwidevine-modular_decryptor.so` from the TV
  (8260 bytes, SHA256 `d35a1b16…0112c`). You supply your own copy, pulled from
  your own rooted TV.

## Method

`PatchC9CbcsCounter.java` is a Ghidra script. Loaded against the original
library, it:

1. Asserts the 28 bytes at the patch offset match the known original block
   (`fff7e0ed…0792`), refusing to run otherwise.
2. Assembles the replacement ARM Thumb sequence, which resets the counter
   (`str #0` into the counter slot) only when `cipher_mode == 1` (CBC) and
   `encrypt_blocks != 0`, leaving every other branch, pointer calculation, and
   failure path untouched.
3. Emulates the patched block with synthetic integers for the CTR case, the
   non-pattern CBC case, and two CBCS pattern cases, asserting the counter is
   reset exactly when expected and pointer accounting is preserved. No native
   crypto, keys, device data, or media buffers are involved.
4. Writes the corrected library and prints the 28 replacement bytes
   (`079a5c46…0792`), which produce the patched SHA256 `979d4686…2db5df`.

The emulator stops just before the unchanged final `STR` instruction (a Ghidra
legacy-ARM decode quirk at that branch target); those bytes are verified to be
unchanged by byte comparison instead.

## Offline check without Ghidra

`verify.py` in the repository root reproduces the same result arithmetically:
it confirms a supplied original matches the known SHA256 and that splicing in
the 28 replacement bytes yields the known patched SHA256. This is exactly the
check the on-TV installer performs before it mounts anything.

```bash
python3 ../verify.py /path/to/your/libwidevine-modular_decryptor.so
```

## Semantics

The bug: the affected decryptor advances a CTR-style byte counter across
protected subsamples. CBCS with a constant IV must reset the pattern cryptor per
subsample (see Shaka Packager's `aes_pattern_cryptor.cc` and
`decryptor_source.cc`). Single-subsample samples therefore succeed while
multi-subsample HD samples (720p/1080p) are decrypted incorrectly. The patch
restores the per-subsample reset.

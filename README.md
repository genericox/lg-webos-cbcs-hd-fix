# webOS CBCS HD fix

Enable **1080p (HD) playback** of CBCS-encrypted DRM video on old, **rooted** LG
webOS TVs by repairing a decryption bug in the TV's **own** Widevine modular
decryptor.

On some early LG webOS firmwares, the modular Widevine decryptor advances a
CTR-style byte counter *across* protected subsamples instead of resetting it for
each CBCS subsample. The result: streams whose first sample has a single
encrypted subsample play fine (e.g. 540p), but 720p/1080p — which split a sample
into 2 or 4 encrypted subsamples — fail with a native media error even though
the license and content key are valid. This tool applies a **28-byte** in-place
correction that resets the pattern cryptor's counter for each protected
subsample (only when `cipher_mode == CBC (1)` and `encrypt_blocks != 0`),
matching the public behaviour of [Shaka Packager's constant-IV CBCS
cryptor](https://github.com/shaka-project/shaka-packager/blob/main/packager/media/base/aes_pattern_cryptor.cc).

> **Scope: HD / 1080p only.** UHD / 2160p is **not** fixed — it still decodes to
> a multicolored mosaic and is out of scope for this tool.

Full public diagnosis and evidence: **Ravboni/race-weekend#24**
(https://github.com/Ravboni/race-weekend/issues/24).

## What it does / does not do

- It reads the decryptor library **already on your TV**, verifies its SHA256
  against a table of known-good originals, applies the 28-byte correction,
  verifies the result's SHA256, and installs it as a **read-only bind-mount
  overlay** plus a webosbrew boot hook.
- It **never overwrites** the firmware file on disk, and keeps a **full backup**
  of the original. `disable` detaches the overlay and restores the original.
- It is matched by **library hash**, not by MAC address, so it generalizes
  across TVs that ship the same affected library. An **unrecognized** library is
  **refused** with a clear message — nothing is changed.
- It does **not** download, contain, or redistribute any LG binary. The only LG
  bytes in this repository are the 28-byte original instruction block needed to
  identify and verify the patch target.

## Requirements

- An LG webOS TV **rooted** via the [Homebrew Channel / webosbrew](https://github.com/webosbrew)
  (this installs a boot hook under `/var/lib/webosbrew/init.d`).
- Root shell access to the TV (e.g. over SSH).
- A TV whose decryptor library is in the [known-firmware table](KNOWN_FIRMWARES.md).

## Supported firmware

| TV / firmware | webOS | Widevine | status |
|---|---|---|---|
| LG OLED C9 (OLED55C9PSA) fw 05.50.00 | 4.5 | 14 | verified 1080p playback |

Other TVs/firmwares can be added — see [KNOWN_FIRMWARES.md](KNOWN_FIRMWARES.md).
If your library is not listed, the tool refuses to patch it; please open an
issue with the SHA256 it reports and your TV model/firmware/webOS/Widevine
versions so support can be added safely.

## Usage

Copy `cbcs-hd-fix.sh` to your TV and run it as root:

```bash
# inspect only - changes nothing, tells you if your library is recognized
sh cbcs-hd-fix.sh check
```

```bash
# back up the original, patch, bind-mount the overlay, install the boot hook
sh cbcs-hd-fix.sh apply
```

```bash
# report current state
sh cbcs-hd-fix.sh status
```

```bash
# roll back: detach the overlay and stop the boot hook (then restart the app)
sh cbcs-hd-fix.sh disable
```

After `apply`, restart your streaming app and try a 1080p stream. A media
process that is already running may keep the old library mapped until it exits,
so fully close and reopen the app.

### Verify offline before you trust it (optional)

On your computer, with a copy of the decryptor you pulled from **your own** TV:

```bash
python3 verify.py /path/to/libwidevine-modular_decryptor.so
```

It confirms the file is a recognized original and that applying the 28-byte diff
reproduces the exact known patched SHA256 — the same check the on-TV script
performs before it installs anything.

## How the patch was derived

The 28 corrected bytes were produced reproducibly with Ghidra; the generator and
the exact offsets, hashes, and old/new byte blocks are in
[`reproduce/`](reproduce/) and [KNOWN_FIRMWARES.md](KNOWN_FIRMWARES.md).

## Safety & legal

See [SECURITY.md](SECURITY.md). In short: this corrects a **decryption-
correctness bug** for content you are already licensed to watch. It does **not**
bypass license acquisition, key exchange, or output protection, and it ships no
LG binary. Rooting and patching your TV is at your own risk and may void your
warranty.

## License

MIT — see [LICENSE](LICENSE). The MIT license covers the scripts in this
repository, not the LG firmware they operate on.

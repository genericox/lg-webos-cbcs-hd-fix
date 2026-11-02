# Safety, security and legal note

## What this tool is

A one-file shell script that repairs a **decryption-correctness bug** in the
Widevine *modular decryptor* that ships on certain old LG webOS TVs. On the
affected firmware the decryptor advances a CTR-style counter across protected
subsamples instead of resetting it per CBCS subsample, so multi-subsample HD
samples (720p/1080p) are decrypted incorrectly and rejected by the native media
pipeline, while single-subsample samples (e.g. 540p) succeed. The fix makes the
decryptor reset that counter per subsample, as public CBCS implementations do.

## What it deliberately does **not** do

- It does **not** bypass or weaken license acquisition. The CDM still requests a
  license and derives the content key exactly as before.
- It does **not** extract, export, or log keys, license bodies, IVs, PSSH/`tenc`
  data, or stream URLs.
- It does **not** disable or downgrade output protection (HDCP) or robustness.
- It does **not** enable playback of anything you are not already licensed to
  watch. It only lets a **licensed** HD stream decrypt correctly instead of
  failing.
- It does **not** contain, download, or redistribute any LG/Widevine binary. The
  only LG-derived bytes in this repository are the 28-byte original instruction
  block at the patch offset, retained solely to identify the target and verify
  the result. The tool patches the library **already present on your own TV**.

## How it protects your TV

- The firmware file `/usr/lib/svp/libwidevine-modular_decryptor.so` is **never
  overwritten**. The correction is applied as a `mount --bind` overlay of a
  verified payload, and a full copy of the original is saved to
  `/var/lib/webos-cbcs-hd-fix/`. The tool attempts a `remount,bind,ro` and
  verifies the read-only flag in `/proc/mounts`; where the kernel does not
  accept it, the overlay stays writable but this is reported, and integrity is
  guaranteed regardless by the SHA256 gate re-checked at every boot.
- Every step is **hash-gated**. The tool refuses to act unless the live library
  matches a known-good original SHA256, and it refuses to install a built
  payload unless its SHA256 matches the known patched value. A mismatch aborts
  before anything is mounted.
- It is matched by **library hash**, never by a device MAC, so it will not
  misfire on a different firmware — an unrecognized library is simply refused.
- It never stacks overlays and never writes to a library path that is already a
  bind-mount; run `disable` first.
- `disable` creates a marker that stops the boot hook and detaches the overlay,
  restoring the untouched firmware file.

## Risk acknowledgement

Rooting a TV and modifying system libraries is inherently risky and may void
your warranty or destabilize the device. This tool is provided as-is, without
warranty of any kind (see [LICENSE](LICENSE)). Use it only on a TV you own and
have deliberately rooted, and only if you understand and accept that risk.

## Reporting

If your library is not recognized, or you find a defect, please open an issue
with the SHA256 the tool reports plus your TV model, firmware, webOS, and
Widevine versions. Do **not** attach keys, license data, or captured streams.

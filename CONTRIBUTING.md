# Contributing

Thanks for helping. This tool patches a system library on someone's TV, so the
rules here are stricter than usual. Most contributions are **test reports**, so
those come first.

## Ground rules (everyone)

1. **Only your own TV.** Test only on a TV you own and rooted on purpose. Don't
   test on a TV someone else is using, and don't run `apply` on a borrowed TV.
2. **Never share secrets or LG files.** Never attach, paste or link:
   - any LG/Widevine binary, including `libwidevine-modular_decryptor.so`
     (original or patched), firmware images or `.ipk` files;
   - keys, license requests/responses, PSSH/`tenc` boxes, IVs or KIDs;
   - stream or manifest URLs, tokens, cookies, account names, HAR or pcap
     captures, or recorded video of protected content;
   - your TV's serial number, MAC address or public IP.

   A SHA256 hash, a file size and the 28-byte old/new blocks are enough to
   describe a library. Reports that contain any of the above will be deleted.
3. **Never force an unknown library.** If `check` says `NOT RECOGNIZED`, stop
   and report it. Don't add your hash to the table yourself to get past the
   refusal: the script then installs a patch that has never been checked
   against your bytes.
4. **Report 2160p separately from 1080p.** 1080p is the baseline every
   firmware entry needs. 2160p is verified on the C9 only; on other TVs it may
   depend on the decoder and the source, so a 2160p failure is useful data but
   not necessarily a bug in this tool. Before blaming the patch for low 4K
   frame rates, make sure the Live TV app is closed (see the README).
5. **This repository is not about getting around DRM.** It does not help with
   license acquisition, output protection (HDCP), region locks or content you
   aren't licensed to watch, and such requests will be closed.

## Testing on your TV

### Before you start

- Root via the [Homebrew Channel / webosbrew](https://github.com/webosbrew) and
  get a root shell (SSH or telnet).
- Write down your TV's details:
  ```sh
  luna-send -n 1 -f luna://com.webos.service.tv.systemproperty/getSystemInfo '{"keys":["modelName","firmwareVersion","sdkVersion"]}'
  ```
  If that command isn't available, read the model and software version from
  **Settings → General → About This TV**.
- Download `cbcs-hd-fix.sh` from a [release](https://github.com/genericox/lg-webos-cbcs-hd-fix/releases)
  and check it against `SHA256SUMS` on your computer, or use a commit you name
  in your report.
- Know how to undo: `sh cbcs-hd-fix.sh disable`, then reboot the TV if anything
  looks wrong.

### Step 1: `check` (always safe)

```sh
sh cbcs-hd-fix.sh check
```

This changes nothing. Keep its full output for your report.

- `RECOGNIZED original`: go to step 2.
- `NOT RECOGNIZED`: stop here and open an issue with the SHA256 it printed and
  your TV details. That report is already useful.
- `NOT FOUND`: your TV has no modular decryptor at that path. Report it too;
  it means the tool doesn't apply to that model.

### Step 2: baseline, before applying

In the streaming app you use, play the same title at **540p** and at
**1080p**, and note what happens. On an affected TV, 540p plays and 1080p fails
with a media error. If 1080p already plays before the fix, say so: your
firmware may not have the bug.

### Step 3: apply and test

```sh
sh cbcs-hd-fix.sh apply
sh cbcs-hd-fix.sh status
```

Fully close and reopen the streaming app, then test:

| test | what to check |
|---|---|
| 540p | still plays (no regression) |
| 720p | plays with a clean picture |
| 1080p AVC (H.264) | plays with a clean picture |
| 1080p HEVC (H.265), if the service offers it | plays with a clean picture |
| 2160p, if the TV and service support it (optional) | clean picture; note the frame rate and whether Live TV was closed |
| reboot the TV, run `status` and play 1080p again | the fix came back after boot |
| `disable`, restart the app, play 1080p | back to the original behaviour |

**What counts as "verified":** a stream you watched play on the screen for at
least a few minutes at 1080p, with no green or blocky frames and no stutter
that wasn't there before. The app reporting success or the player switching to
1080p is not enough on its own; the C9 entry was only marked verified after
real on-screen playback.

If the TV crashes, freezes or the app loses video, run `disable`, reboot, and
report what happened. That is a valuable result.

### After an LG firmware update

A new firmware changes the library hash. The boot hook then skips the patch
and writes `skipped: library … not recognized` to
`/var/lib/webos-cbcs-hd-fix/status.log`. That is expected. Run `check` again
and report the new hash if it's not recognized.

### Test report template

Open an issue and fill this in:

```text
TV model:            (e.g. OLED55C9PSA)
Firmware version:    (e.g. 05.50.00)
webOS version:
Widevine version:    (if you know it)
Tool version:        (release tag or commit)
Streaming app/service + how you picked the resolution:

`sh cbcs-hd-fix.sh check` output:
(paste)

Before apply:  540p = plays / fails    1080p = plays / fails
After apply:   540p =        720p =        1080p AVC =        1080p HEVC =
2160p (optional): picture clean?   fps?   Live TV closed?
After reboot:  status shows overlay active? 1080p plays?
After disable: back to original behaviour?

Anything odd (crashes, artifacts, error codes):
```

## Adding support for a new firmware

Follow the steps in [KNOWN_FIRMWARES.md](KNOWN_FIRMWARES.md#adding-a-new-tv--firmware).
A pull request that adds a firmware must:

- change all three tables together: `known_firmwares()` in `cbcs-hd-fix.sh`,
  `KNOWN` in `verify.py` and a new section in `KNOWN_FIRMWARES.md`;
- keep the patch at **28 bytes** at a single offset, and explain how the bytes
  were derived (a Ghidra script in `reproduce/` like the C9 one is preferred);
- include the output of `python3 verify.py` on your original library, showing
  `OK - reproduces the known patched hash`;
- include a test report from **your own TV** with real 1080p playback.
  Entries without one can be listed as `untested` in the docs, but won't be
  added to the script's table;
- contain no LG binary, only hashes, size, offset and the old/new byte blocks.

## Changing the script

- It runs on the TV's **busybox `sh`**: POSIX shell only, no bashisms. Run
  `shellcheck -s sh cbcs-hd-fix.sh` before opening a PR.
- Keep **LF** line endings (`.gitattributes` enforces this; a CRLF script fails
  on the TV).
- Keep the safety properties in [SECURITY.md](SECURITY.md): every write is
  hash-gated, the firmware file is never overwritten, the original is backed
  up, overlays never stack, matching is by library hash and **never** by MAC,
  serial or model name, and `disable` always restores the original.
- Don't add network access, downloads or telemetry to the script or the boot
  hook.
- Say in the PR which commands you ran on a real TV (`check`, `apply`,
  `status`, reboot, `disable`, `enable`) and on which model and firmware.

## Security issues

If you find a way the tool could damage a TV, patch the wrong library or leak
data, don't put the details in a public issue. Use GitHub's private
vulnerability reporting on this repository, or open an issue that only says
you have something to report privately.

By contributing you agree that your contribution is licensed under the
[MIT License](LICENSE).

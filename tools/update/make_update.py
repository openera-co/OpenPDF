#!/usr/bin/env python3
"""
Builds an in-app update for Reel.

    make_update.py --new app-release.apk --old 1.3.0.apk [--old 1.2.0.apk ...] --base-url URL --out DIR

Writes DIR/update.json, DIR/reel-<version>.apk and one patch per old APK. A patch rebuilds the new
APK byte for byte from the APK already installed: zip entries whose bytes didn't change (the speech
model, fonts, shaders...) are copied from the old file, everything else is shipped. The app checks
the SHA-256 of the result before installing, so a bad patch can never install anything.

Patch format (RPT1): magic "RPT1", then a zlib stream of ops:
  'C' <u64 old offset> <u64 length>   copy bytes from the installed APK
  'D' <u64 length> <bytes>            literal bytes
  'E'                                 end
"""
import argparse, hashlib, json, os, shutil, struct, subprocess, zipfile, zlib

MIN_COPY = 4096


def entries(path):
    """(data offset, data length, sha1) for each stored or deflated zip entry."""
    out = []
    with open(path, "rb") as f, zipfile.ZipFile(path) as z:
        for info in z.infolist():
            f.seek(info.header_offset)
            h = f.read(30)
            if h[:4] != b"PK\x03\x04":
                continue
            n, e = struct.unpack("<HH", h[26:30])
            start = info.header_offset + 30 + n + e
            size = info.compress_size
            if size < MIN_COPY:
                continue
            f.seek(start)
            digest = hashlib.sha1()
            left = size
            while left:
                b = f.read(min(left, 1 << 20)); digest.update(b); left -= len(b)
            out.append((start, size, digest.hexdigest()))
    return out


def make_patch(old, new, dest):
    old_index = {h: (o, l) for o, l, h in entries(old)}
    copies = sorted((o, l, old_index[h]) for o, l, h in entries(new) if h in old_index)
    comp = zlib.compressobj(9)
    out = open(dest, "wb")
    out.write(b"RPT1")
    copied = literal = 0
    with open(new, "rb") as f:
        size = os.path.getsize(new)
        cur = 0

        def lit(upto):
            nonlocal cur, literal
            if upto <= cur:
                return
            f.seek(cur)
            data = f.read(upto - cur)
            out.write(comp.compress(b"D" + struct.pack("<Q", len(data)) + data))
            literal += len(data)
            cur = upto

        for start, length, (oo, ol) in copies:
            if start < cur:
                continue
            lit(start)
            out.write(comp.compress(b"C" + struct.pack("<QQ", oo, ol)))
            copied += ol
            cur = start + length
        lit(size)
    out.write(comp.compress(b"E"))
    out.write(comp.flush())
    out.close()
    return copied, literal


def apply_patch(old, patch, dest):
    """Reference applier, used to self-check every patch."""
    with open(patch, "rb") as p:
        assert p.read(4) == b"RPT1"
        data = zlib.decompress(p.read())
    i = 0
    with open(old, "rb") as o, open(dest, "wb") as out:
        while True:
            op = data[i:i + 1]; i += 1
            if op == b"E":
                break
            if op == b"C":
                off, ln = struct.unpack("<QQ", data[i:i + 16]); i += 16
                o.seek(off); out.write(o.read(ln))
            elif op == b"D":
                (ln,) = struct.unpack("<Q", data[i:i + 8]); i += 8
                out.write(data[i:i + ln]); i += ln
            else:
                raise ValueError("bad op")


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for b in iter(lambda: f.read(1 << 20), b""):
            h.update(b)
    return h.hexdigest()


def badging(apk):
    """(versionCode, versionName) via aapt2 from the Android SDK."""
    sdk = os.environ.get("ANDROID_HOME", "/opt/android-sdk")
    tools = sorted(os.listdir(os.path.join(sdk, "build-tools")))[-1]
    out = subprocess.run([os.path.join(sdk, "build-tools", tools, "aapt2"), "dump", "badging", apk], capture_output=True, text=True).stdout
    line = out.splitlines()[0]
    code = int(line.split("versionCode='")[1].split("'")[0])
    name = line.split("versionName='")[1].split("'")[0]
    return code, name


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--new", required=True)
    ap.add_argument("--old", action="append", default=[])
    ap.add_argument("--base-url", required=True, help="where the files in --out will be served from")
    ap.add_argument("--out", required=True)
    ap.add_argument("--notes", default="")
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    code, name = badging(a.new)
    base = a.base_url.rstrip("/")
    full = f"reel-{name}.apk"
    shutil.copyfile(a.new, os.path.join(a.out, full))
    target = sha256(a.new)
    manifest = {
        "versionCode": code, "versionName": name, "notes": a.notes,
        "apk": {"url": f"{base}/{full}", "size": os.path.getsize(a.new), "sha256": target},
        "patches": [],
    }
    for old in a.old:
        ocode, oname = badging(old)
        pname = f"reel-{ocode}-to-{code}.rpatch"
        ppath = os.path.join(a.out, pname)
        copied, literal = make_patch(old, a.new, ppath)
        check = ppath + ".check"
        apply_patch(old, ppath, check)
        ok = sha256(check) == target
        os.remove(check)
        if not ok:
            raise SystemExit(f"patch from {oname} does not rebuild the new APK")
        manifest["patches"].append({"from": ocode, "url": f"{base}/{pname}", "size": os.path.getsize(ppath), "sourceSha256": sha256(old)})
        print(f"{oname} -> {name}: patch {os.path.getsize(ppath) / 1e6:.1f} MB (copies {copied / 1e6:.1f} MB, ships {literal / 1e6:.1f} MB)")
    with open(os.path.join(a.out, "update.json"), "w") as f:
        json.dump(manifest, f, indent=2)
    print(f"full APK {os.path.getsize(a.new) / 1e6:.1f} MB; wrote {a.out}/update.json")


if __name__ == "__main__":
    main()

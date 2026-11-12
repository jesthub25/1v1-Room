#!/usr/bin/env python3
"""Paper sunucusu ve (varsa) WorldEdit indirir.

Kullanim: fetch.py <surum|latest> <hedef_klasor>
Cozumlenen Minecraft surumunu stdout'a yazar.
"""
import json
import os
import sys
import urllib.parse
import urllib.request

UA = "1v1Arena-CI/1.0 (github.com/jesthub25/1v1-Room)"


def get_json(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.loads(r.read().decode("utf-8"))


def download(url, dest):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=300) as r, open(dest, "wb") as f:
        while True:
            chunk = r.read(1 << 16)
            if not chunk:
                break
            f.write(chunk)


def vkey(v):
    parts = []
    for p in v.replace("-", ".").split("."):
        parts.append(int(p) if p.isdigit() else -1)
    return parts


def latest_build_v3(version):
    try:
        b = get_json(f"https://fill.papermc.io/v3/projects/paper/versions/{version}/builds/latest")
    except Exception as e:  # noqa
        log(f"fill v3 build sorgusu basarisiz ({version}): {e}")
        return None
    dl = (b.get("downloads") or {}).get("server:default")
    if not dl:
        return None
    return {"url": dl["url"], "channel": str(b.get("channel", "")).upper()}


def latest_build_v2(version):
    try:
        data = get_json(f"https://api.papermc.io/v2/projects/paper/versions/{version}/builds")
        builds = data.get("builds") or []
        if not builds:
            return None
        b = builds[-1]
        name = b["downloads"]["application"]["name"]
        return {
            "url": f"https://api.papermc.io/v2/projects/paper/versions/{version}/builds/{b['build']}/downloads/{name}",
            "channel": str(b.get("channel", "")).upper(),
        }
    except Exception as e:  # noqa
        log(f"v2 build sorgusu basarisiz ({version}): {e}")
        return None


def all_versions():
    try:
        data = get_json("https://fill.papermc.io/v3/projects/paper")
        groups = data.get("versions") or {}
        versions = []
        for lst in groups.values():
            versions.extend(lst)
        if versions:
            return sorted(set(versions), key=vkey, reverse=True)
    except Exception as e:  # noqa
        log(f"fill v3 surum listesi alinamadi: {e}")
    data = get_json("https://api.papermc.io/v2/projects/paper")
    return sorted(set(data["versions"]), key=vkey, reverse=True)


def log(msg):
    print(msg, file=sys.stderr, flush=True)


def resolve(spec):
    if spec.endswith(".x"):
        prefix = spec[:-2]
        candidates = [v for v in all_versions() if v == prefix or v.startswith(prefix + ".")]
        log(f"{spec} icin adaylar: {candidates}")
        for v in candidates:
            b = latest_build_v3(v) or latest_build_v2(v)
            if b:
                return v, b
        raise SystemExit(f"Paper {spec} icin build bulunamadi")
    if spec != "latest":
        b = latest_build_v3(spec) or latest_build_v2(spec)
        if not b:
            raise SystemExit(f"Paper {spec} icin build bulunamadi")
        return spec, b
    for v in all_versions():
        if any(x in v for x in ("pre", "rc", "snapshot")):
            continue
        b = latest_build_v3(v) or latest_build_v2(v)
        if b and b["channel"] in ("STABLE", "DEFAULT", ""):
            return v, b
        log(f"{v}: stabil build yok ({b['channel'] if b else 'yok'}), atlaniyor")
    log("Tum surumler: " + ", ".join(all_versions()[:20]))
    raise SystemExit("Stabil Paper surumu bulunamadi")


def fetch_worldedit(version, plugins_dir):
    for loaders in (["paper"], ["bukkit"], ["spigot"]):
        q = urllib.parse.urlencode({
            "game_versions": json.dumps([version]),
            "loaders": json.dumps(loaders),
        })
        try:
            versions = get_json(f"https://api.modrinth.com/v2/project/worldedit/version?{q}")
        except Exception as e:  # noqa
            log(f"Modrinth sorgusu basarisiz: {e}")
            return False
        # Hedef surume en yakin WorldEdit: destekledigi en yeni oyun surumu hedefe en yakin olan
        # (Modrinth'te yeni WorldEdit surumleri bazen eski oyun surumlerini de listeliyor ama calismiyor).
        def newest_gv(v):
            gvs = [g for g in (v.get("game_versions") or []) if g[:1].isdigit()]
            return max(gvs, key=vkey) if gvs else version

        versions = sorted(versions, key=lambda v: vkey(newest_gv(v)))
        for ver in versions:
            files = ver.get("files") or []
            primary = next((f for f in files if f.get("primary")), files[0] if files else None)
            if primary and primary["filename"].endswith(".jar"):
                download(primary["url"], os.path.join(plugins_dir, "WorldEdit.jar"))
                log(f"WorldEdit {ver.get('version_number')} indirildi")
                return True
    log("Bu surum icin WorldEdit bulunamadi, WorldEdit testi atlanacak")
    return False


def main():
    spec, out = sys.argv[1], sys.argv[2]
    os.makedirs(os.path.join(out, "plugins"), exist_ok=True)
    version, build = resolve(spec)
    log(f"Paper {version} indiriliyor: {build['url']}")
    download(build["url"], os.path.join(out, "paper.jar"))
    has_we = fetch_worldedit(version, os.path.join(out, "plugins"))
    with open(os.path.join(out, "worldedit.txt"), "w") as f:
        f.write("1" if has_we else "0")
    print(version)


if __name__ == "__main__":
    main()

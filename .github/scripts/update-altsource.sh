#!/usr/bin/env bash
# Regenerate the AltSource JSON for the built iOS app. AltStore Classic and
# SideStore share this format; AltStore PAL is intentionally never produced
# (requires Apple notarization + App Store Connect, incompatible with the
# per-user re-signed sideload IPA this source distributes).
#
# The app's own archived Info.plist is the source of truth for versioning;
# everything the store shows is derived from the fresh build. Pure Python +
# json for the metadata so it runs on any CI (plutil/stat are macOS-only).
#
# Usage: update-altsource.sh --ipa <file> --source <file> --base <url> [--ipa-name <file>]
#   --ipa       path to the archive (.ipa) just built (read only)
#   --source    AltSource JSON to write (created/overwritten)
#   --base      base URL the IPA is downloadable from (no trailing slash), e.g.
#               https://github.com/depau/loak/releases/download/nightly
#   --ipa-name  IPA filename the store should download (default "Loak.ipa").
#               Nightly publishes "Loak-nightly.ipa" alongside its manifest.
#
# One app, one current version entry: this source tracks whichever build was
# published last, and the store compares version[0] with what's installed.
set -euo pipefail

base=""; ipa=""; src=""; ipa_name="Loak.ipa"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --ipa) ipa="$2"; shift 2 ;;
    --source) src="$2"; shift 2 ;;
    --base) base="$2"; shift 2 ;;
    --ipa-name) ipa_name="$2"; shift 2 ;;
    *) echo "usage: $0 --ipa <file> --source <file> --base <url> [--ipa-name <file>]" >&2; exit 1 ;;
  esac
done
[[ -n "$ipa" && -n "$src" && -n "$base" ]] || { echo "missing required arguments" >&2; exit 1; }

python3 - "$ipa" "$src" "$base" "$ipa_name" <<'PYEOF'
import json, plistlib, sys, zipfile
from datetime import datetime, timezone

ipa_path, out_path, base, ipa_name = sys.argv[1:5]

with zipfile.ZipFile(ipa_path) as zf:
    app_dir = next(n for n in zf.namelist()
                   if n.count("/") == 2 and n.endswith(".app/"))
    info = plistlib.load(zf.open(f"{app_dir}Info.plist"))

size = __import__("os").path.getsize(ipa_path)

bundle_id = info["CFBundleIdentifier"]
short = info["CFBundleShortVersionString"]
build = info["CFBundleVersion"]
min_os = info["MinimumOSVersion"]
name = "Lo'ak"

# Privacy usage strings AltStore checks installs against (only what exists).
privacy_candidates = [
    "NSCameraUsageDescription", "NSMicrophoneUsageDescription",
    "NSBluetoothAlwaysUsageDescription", "NSBluetoothPeripheralUsageDescription",
    "NSPhotoLibraryUsageDescription", "NSPhotoLibraryAddUsageDescription",
    "NSLocationWhenInUseUsageDescription", "NSLocationAlwaysUsageDescription",
    "NSCalendarsUsageDescription", "NSContactsUsageDescription", "NSRemindersUsageDescription",
    "NSFaceIDUsageDescription", "NSMotionUsageDescription", "NSHealthShareUsageDescription",
    "NSHealthUpdateUsageDescription", "NSAppleMusicUsageDescription", "NSLocalNetworkUsageDescription",
    "NSSpeechRecognitionUsageDescription", "NSUserTrackingUsageDescription",
    "NSFallDetectionUsageDescription", "NSSiriUsageDescription",
]
privacy = {k: info[k] for k in privacy_candidates if k in info}

icon_url = "https://raw.githubusercontent.com/depau/loak/main/fastlane/metadata/android/en-US/images/icon.png"
screenshot_urls = [
    "https://github.com/depau/loak/blob/main/fastlane/metadata/android/en-US/images/phoneScreenshots/1.jpg?raw=true",
    "https://github.com/depau/loak/blob/main/fastlane/metadata/android/en-US/images/phoneScreenshots/2.jpg?raw=true",
    "https://github.com/depau/loak/blob/main/fastlane/metadata/android/en-US/images/phoneScreenshots/3.jpg?raw=true",
    "https://github.com/depau/loak/blob/main/fastlane/metadata/android/en-US/images/phoneScreenshots/4.jpg?raw=true",
]
now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

source = {
    "name": f"{name} Repository",
    "identifier": "eu.depau.loak.repo",
    "website": "https://github.com/depau/loak",
    "iconURL": icon_url,
    "apps": [{
        "name": name,
        "bundleIdentifier": bundle_id,
        "developerName": "depau",
        "subtitle": "Subsonic music streaming app",
        "localizedDescription": f"{name} is a modern Subsonic/OpenSubsonic music "
            "streaming client for Android and iOS. Stream and download music, "
            "scrobble to your server, listen with lyrics, radio, ReplayGain, "
            "equaliser, and more.",
        "category": "entertainment",
        "iconURL": icon_url,
        "versions": [{
            "version": short,
            "buildVersion": build,
            "marketingVersion": f"{name} {short} ({build})",
            "date": now,
            "downloadURL": f"{base}/{ipa_name}",
            "size": size,
            "minOSVersion": min_os,
        }],
        "appPermissions": {
            "entitlements": [],
            "privacy": privacy,
        },
        "screenshots": [{"imageURL": u} for u in screenshot_urls],
        "tintColor": "ffffff",
    }],
    "news": [{
        "title": "New version available",
        "identifier": f"{bundle_id}-{build}",
        "caption": f"{name} {short} ({build}) is ready to update.",
        "date": now,
        "tintColor": "#F54F32",
        "appID": bundle_id,
        "notify": True,
    }],
}

with open(out_path, "w") as f:
    json.dump(source, f, indent=2, ensure_ascii=False)
    f.write("\n")

print(f"wrote {out_path} ({name} {short} ({build}), {size} bytes)")
PYEOF

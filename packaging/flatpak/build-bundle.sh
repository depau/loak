#!/usr/bin/env bash
# Repackage a packaged Lo'ak desktop app image as a single-file Flatpak bundle:
#
#   dist/loak-<version>-linux-<arch>.flatpak
#
# Usage: packaging/flatpak/build-bundle.sh --version VERSION [--app-image DIR] [--output FILE]
#
# The app image is the jpackage tree at loakDesktop/build/compose/binaries/main/app, the same
# one the portable Linux tarball uses. Nothing is compiled: the tree is committed straight to
# an OSTree repository with `flatpak build-export`, so no runtime or SDK is needed here. The
# bundle names Flathub as its runtime repository, and `flatpak install` fetches the freedesktop
# runtime from there when it is missing.
set -euo pipefail
trap 'echo "build-bundle.sh: line $LINENO: \"$BASH_COMMAND\" failed (exit $?)" >&2' ERR

APP_ID=eu.depau.loak
RUNTIME_VERSION=26.08
FLATHUB=https://dl.flathub.org/repo/flathub.flatpakrepo

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"

VERSION=""
APP_IMAGE="$ROOT/loakDesktop/build/compose/binaries/main/app"
OUTPUT=""
while [ $# -gt 0 ]; do
	case "$1" in
		--version) VERSION="${2:?--version needs a value}"; shift 2 ;;
		--app-image) APP_IMAGE="${2:?--app-image needs a value}"; shift 2 ;;
		--output) OUTPUT="${2:?--output needs a value}"; shift 2 ;;
		-h | --help) sed -n '2,14p' "$0"; exit 0 ;;
		*) echo "unknown argument: $1" >&2; exit 2 ;;
	esac
done
[ -n "$VERSION" ] || { echo "error: --version is required" >&2; exit 2; }

case "$(uname -m)" in
	x86_64 | amd64) ARCH=x86_64 ;;
	aarch64 | arm64) ARCH=aarch64 ;;
	*) echo "unsupported architecture $(uname -m)" >&2; exit 2 ;;
esac

LAUNCHER="$APP_IMAGE/Loak/bin/Loak"
[ -x "$LAUNCHER" ] || {
	echo "error: no packaged app image at $APP_IMAGE (run a Linux package task first)" >&2
	exit 1
}
for tool in flatpak ostree python3; do
	command -v "$tool" >/dev/null || { echo "error: $tool not found" >&2; exit 1; }
done

OUTPUT="${OUTPUT:-$ROOT/dist/loak-$VERSION-linux-$ARCH.flatpak}"
mkdir -p "$(dirname "$OUTPUT")"
OUTPUT="$(cd "$(dirname "$OUTPUT")" && pwd)/$(basename "$OUTPUT")"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
BUILD="$WORK/build"

# jpackage's launcher looks for lib/ next to its bin/ directory, so keep the app image's
# top-level layout instead of flattening it.
mkdir -p "$BUILD/files"
cp -a "$APP_IMAGE/Loak/bin" "$BUILD/files/bin"
cp -a "$APP_IMAGE/Loak/lib" "$BUILD/files/lib"

install -Dm644 "$HERE/$APP_ID.desktop" "$BUILD/files/share/applications/$APP_ID.desktop"
install -Dm644 "$HERE/$APP_ID.metainfo.xml" "$BUILD/files/share/metainfo/$APP_ID.metainfo.xml"
sed -i -e "s/@VERSION@/$VERSION/" -e "s/@DATE@/$(date -u +%Y-%m-%d)/" \
	"$BUILD/files/share/metainfo/$APP_ID.metainfo.xml"
install -Dm644 "$ROOT/loakDesktop/icons/loak.png" \
	"$BUILD/files/share/icons/hicolor/256x256/apps/$APP_ID.png"

# `flatpak build-bundle` embeds the AppStream catalog and 64/128 px icons from
# share/app-info when writing a .flatpak or an OCI image; without them a feed
# lists the app without its name, summary or icon. The bundle builder reads the
# catalog from share/app-info/xmls/<id>.xml.gz and expects a <components> root,
# while the metainfo is a single <component>: wrap it (and install the two icon
# sizes) rather than depend on `appstreamcli compose`, whose compose action is
# missing from Ubuntu 24.04's AppStream 1.0.
mkdir -p "$BUILD/files/share/app-info/xmls" "$BUILD/files/share/app-info/icons/flatpak/64x64" \
	"$BUILD/files/share/app-info/icons/flatpak/128x128"
python3 - "$APP_ID" "$BUILD/files/share/metainfo/$APP_ID.metainfo.xml" \
	"$BUILD/files/share/app-info/xmls/$APP_ID.xml.gz" <<'PY'
import gzip
import pathlib
import sys
import xml.etree.ElementTree as ET

app_id, source, target = sys.argv[1:4]
text = pathlib.Path(source).read_text()
if text.lstrip().startswith("<?xml"):
	text = text.split("?>", 1)[1]
if ET.fromstring(text).tag != "component":
	sys.exit(f"error: {source} is not a single AppStream component")
catalog = (
	'<?xml version="1.0" encoding="UTF-8"?>\n<components version="1.0">\n'
	+ text.lstrip()
	+ "\n</components>\n"
)
with gzip.open(target, "wb") as catalog_file:
	catalog_file.write(catalog.encode())
PY
install -Dm644 "$HERE/icons/64x64.png" \
	"$BUILD/files/share/app-info/icons/flatpak/64x64/$APP_ID.png"
install -Dm644 "$HERE/icons/128x128.png" \
	"$BUILD/files/share/app-info/icons/flatpak/128x128/$APP_ID.png"

cat >"$BUILD/metadata" <<EOF
[Application]
name=$APP_ID
runtime=org.freedesktop.Platform/$ARCH/$RUNTIME_VERSION
sdk=org.freedesktop.Sdk/$ARCH/$RUNTIME_VERSION
command=Loak
EOF

# build-export only accepts a finalized build directory; build-finish writes the [Context]
# permissions here too. No build sandbox is involved: the tree is the packaged app image.
flatpak build-finish \
	--command=Loak \
	--share=network \
	--share=ipc \
	--socket=wayland \
	--socket=fallback-x11 \
	--socket=pulseaudio \
	--device=dri \
	--filesystem=xdg-download \
	--own-name=org.mpris.MediaPlayer2.loak \
	"$BUILD"

REPO="$WORK/repo"
ostree --repo="$REPO" init --mode=archive-z2
flatpak build-export \
	--arch="$ARCH" \
	--subject="Lo'ak $VERSION" \
	--body="Lo'ak $VERSION for Linux ($ARCH)" \
	"$REPO" "$BUILD" stable
flatpak build-bundle \
	--arch="$ARCH" \
	--runtime-repo="$FLATHUB" \
	"$REPO" "$OUTPUT" "$APP_ID" stable

# Prove the bundle imports and carries the app image's launcher before anyone publishes it.
VALIDATION="$WORK/validation"
ostree --repo="$VALIDATION" init --mode=archive-z2
flatpak build-import-bundle "$VALIDATION" "$OUTPUT"
REF="app/$APP_ID/$ARCH/stable"
[ "$(ostree --repo="$VALIDATION" refs)" = "$REF" ] || {
	echo "error: $OUTPUT does not hold $REF" >&2
	exit 1
}
LISTING="$(ostree --repo="$VALIDATION" ls -R "$REF")"
printf '%s\n' "$LISTING" | grep -Eq '^-00[0-9]+ +0 +0 +[0-9]+ /files/bin/Loak$' || {
	echo "error: $OUTPUT has no launcher at /files/bin/Loak" >&2
	exit 1
}
for f in share/applications/$APP_ID.desktop share/metainfo/$APP_ID.metainfo.xml \
	share/icons/hicolor/256x256/apps/$APP_ID.png \
	share/app-info/xmls/$APP_ID.xml.gz \
	share/app-info/icons/flatpak/64x64/$APP_ID.png \
	share/app-info/icons/flatpak/128x128/$APP_ID.png; do
	printf '%s\n' "$LISTING" | grep -Fq " /files/$f" || {
		echo "error: $OUTPUT has no /files/$f" >&2
		exit 1
	}
done

echo "wrote $OUTPUT"

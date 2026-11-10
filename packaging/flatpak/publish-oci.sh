#!/usr/bin/env bash
# Publish the release's Flatpak bundles as OCI images and add them to the static
# OCI index Flatpak remotes read.
#
# Usage:
#   packaging/flatpak/publish-oci.sh --bundles DIR --version VERSION \
#       --repository OWNER/REPO [--registry ghcr.io] [--index-dir DIR] \
#       [--arches "x86_64 aarch64"] [--skip-push]
#
# Each `loak-*-linux-<arch>.flatpak` bundle is imported into a throwaway OSTree
# repository and exported as a single-image OCI layout with `flatpak build-bundle
# --oci`. The image is pushed with skopeo (already logged in) to
# `docker://<registry>/<repository>:<version>-<arch>`. The registry's stored
# manifest and config are then read back, so the index carries the digest the
# registry actually serves, not the local layout's.
#
# The index is written to `<index-dir>/index/static`; `--skip-push` keeps it local
# (useful to test the layout and the index without a registry).
set -euo pipefail
trap 'echo "publish-oci.sh: line $LINENO: \"$BASH_COMMAND\" failed (exit $?)" >&2' ERR

APP_ID=eu.depau.loak
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

BUNDLES=""
VERSION=""
REPOSITORY=""
REGISTRY=ghcr.io
INDEX_DIR=""
ARCHES="x86_64 aarch64"
PUSH=1
while [ $# -gt 0 ]; do
	case "$1" in
		--bundles) BUNDLES="${2:?--bundles needs a value}"; shift 2 ;;
		--version) VERSION="${2:?--version needs a value}"; shift 2 ;;
		--repository) REPOSITORY="${2:?--repository needs a value}"; shift 2 ;;
		--registry) REGISTRY="${2:?--registry needs a value}"; shift 2 ;;
		--index-dir) INDEX_DIR="${2:?--index-dir needs a value}"; shift 2 ;;
		--arches) ARCHES="${2:?--arches needs a value}"; shift 2 ;;
		--skip-push) PUSH=0; shift ;;
		-h | --help) sed -n '2,26p' "$0"; exit 0 ;;
		*) echo "unknown argument: $1" >&2; exit 2 ;;
	esac
done
[ -n "$BUNDLES" ] || { echo "error: --bundles is required" >&2; exit 2; }
[ -n "$VERSION" ] || { echo "error: --version is required" >&2; exit 2; }
[ -n "$REPOSITORY" ] || { echo "error: --repository is required" >&2; exit 2; }
INDEX_DIR="${INDEX_DIR:-$PWD/flatpak-index}"
REPOSITORY="${REPOSITORY,,}"
INDEX="$INDEX_DIR/index/static"

for tool in flatpak ostree python3; do
	command -v "$tool" >/dev/null || { echo "error: $tool not found" >&2; exit 1; }
done
if [ "$PUSH" = 1 ]; then
	command -v skopeo >/dev/null || { echo "error: skopeo not found" >&2; exit 1; }
fi

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

index_push() {
	local oci="$1" arch="$2" tag="$3"
	if [ "$PUSH" = 0 ]; then
		python3 "$HERE/update-index.py" \
			--oci-dir "$oci" --index "$INDEX" --repository "$REPOSITORY" \
			--registry "$REGISTRY" --tags "$tag"
		return
	fi

	local image="docker://$REGISTRY/$REPOSITORY:$tag"
	skopeo copy "oci:$oci" "$image"

	# The registry may re-wrap the manifest (or the client may see its own
	# conversion). Index the bytes it actually serves and the config they name.
	local pushed="$WORK/pushed-$arch"
	mkdir -p "$pushed/blobs/sha256"
	skopeo inspect --raw "$image" >"$pushed/manifest.json"
	skopeo inspect --config "$image" >"$pushed/config.json"
	local manifest_hex config_digest config_hex manifest_size
	manifest_hex="$(sha256sum "$pushed/manifest.json" | cut -d' ' -f1)"
	manifest_size="$(wc -c <"$pushed/manifest.json")"
	config_digest="$(python3 -c \
		'import json,sys; print(json.load(open(sys.argv[1]))["config"]["digest"])' \
		"$pushed/manifest.json")"
	config_hex="${config_digest#sha256:}"
	cp "$pushed/manifest.json" "$pushed/blobs/sha256/$manifest_hex"
	cp "$pushed/config.json" "$pushed/blobs/sha256/$config_hex"
	cat >"$pushed/index.json" <<EOF
{
  "schemaVersion": 2,
  "manifests": [
    {
      "mediaType": "application/vnd.oci.image.manifest.v1+json",
      "digest": "sha256:$manifest_hex",
      "size": $manifest_size
    }
  ]
}
EOF
	python3 "$HERE/update-index.py" \
		--oci-dir "$pushed" --index "$INDEX" --repository "$REPOSITORY" \
		--registry "$REGISTRY" --tags "$tag"
}

for arch in $ARCHES; do
	bundle="$(find "$BUNDLES" -maxdepth 1 -type f \
		-name "loak-*-linux-$arch.flatpak" | head -n 1)"
	[ -n "$bundle" ] || {
		echo "error: no loak-*-linux-$arch.flatpak in $BUNDLES" >&2
		exit 1
	}

	repo="$WORK/repo-$arch"
	oci="$WORK/$APP_ID-$arch.oci"
	ostree --repo="$repo" init --mode=archive-z2
	flatpak build-import-bundle "$repo" "$bundle"
	flatpak build-bundle --oci --arch="$arch" "$repo" "$oci" "$APP_ID" stable
	index_push "$oci" "$arch" "$VERSION-$arch"
done

echo "wrote $INDEX"

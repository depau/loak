#!/usr/bin/env bash
# Builds Nucleus' Tao native library for Linux on this machine's architecture, at the Nucleus
# version Lo'ak uses, with the patches next to this script, into <out dir>/<platform>/. Pass
# that directory to the build as -Ploak.nucleusNative=<out dir> to put the library in Nucleus'
# jar (see loakDesktop/build.gradle.kts).
#
# Needs git, cargo and GTK 3's development files (Debian/Ubuntu: libgtk-3-dev libdbus-1-dev
# libx11-dev libglib2.0-dev).
set -euo pipefail

out=$(realpath -m "${1:?usage: build-native.sh <out dir>}")
here=$(cd "$(dirname "$0")" && pwd)
version=$(sed -n 's/^nucleus = "\(.*\)"/\1/p' "$here/../../gradle/libs.versions.toml")
case $(uname -m) in
	x86_64) platform=linux-x64 ;;
	aarch64) platform=linux-aarch64 ;;
	*) echo "unsupported architecture: $(uname -m)" >&2; exit 1 ;;
esac

src=$(mktemp -d)
trap 'rm -rf "$src"' EXIT
git clone -q --depth 1 --branch "v$version" --filter=blob:none --sparse \
	https://github.com/NucleusFramework/Nucleus.git "$src"
git -C "$src" sparse-checkout set decorated-window-tao/src/main/native
# a patch that no longer applies fails the build: Nucleus was bumped, the patch needs redoing
git -C "$src" apply "$here"/*.patch

native="$src/decorated-window-tao/src/main/native"
cargo build --release --manifest-path "$native/Cargo.toml" --target-dir "$src/target"
mkdir -p "$out/$platform"
cp "$src/target/release/libnucleus_tao.so" "$out/$platform/"
echo "$out/$platform/libnucleus_tao.so (Nucleus $version)"

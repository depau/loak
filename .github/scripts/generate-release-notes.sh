#!/usr/bin/env bash
# Prints the download-table release description for GitHub releases.
#
# Usage: generate-release-notes.sh TITLE TAG DESKTOP_PREFIX PORTABLE_PREFIX ANDROID_FILE IOS_FILE [CHANNEL]
#   TITLE           first line of the description (user-facing, e.g. the version label)
#   TAG             release tag (also the /releases/download/{tag}/ path prefix)
#   DESKTOP_PREFIX  installer/blockmap filename prefix, e.g. "loak-1.0.0-alpha58-"
#   PORTABLE_PREFIX portable archive prefix, e.g. "Loak-" (versionless on releases)
#   ANDROID_FILE    Android APK asset name (e.g. "Lo.ak.apk")
#   IOS_FILE        iOS IPA asset name (e.g. "Loak.ipa")
#   CHANNEL         "stable" (tags) or "nightly" (the fixed nightly tag): picks the
#                   mobile package-manager deep links. Defaults to "stable".
#
# Rendered as HTML so the Android row can span both arch columns (markdown tables
# cannot colspan). Artifact names must match what build-desktop/build-android emit;
# see the Collect/Upload steps in .github/workflows/build.yml.
set -euo pipefail

title="$1"
tag="$2"
dp="$3"   # desktop prefix, includes trailing separator
pp="$4"   # portable prefix, includes trailing separator
apk="$5"
ipa="$6"
channel="${7:-stable}"

dl() { printf '<a href="https://github.com/depau/loak/releases/download/%s/%s">%s</a>' "$tag" "$1" "$2"; }

urlencode() {
	jq -rn --arg value "$1" '$value | @uri'
}

obtainium_link() {
	local config

	config="$(
		jq -cn \
			--arg id "$1" \
			--arg name "$2" \
			--arg apk_regex "$3" \
			'{
				id: $id,
				url: "https://github.com/depau/loak",
				author: "depau",
				name: $name,
				additionalSettings: ({
					includePrereleases: true,
					fallbackToOlderReleases: true,
					verifyLatestTag: false,
					sortMethodChoice: "date",
					versionDetection: true,
					apkFilterRegEx: $apk_regex
				} | tojson)
			}'
	)"

	printf '<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%s">Obtainium</a>' \
		"$(urlencode "$config")"
}

altsource_link() {
	printf '<a href="https://altdirect.app/?url=%s&amp;r=%s">%s</a>' \
		"$(urlencode "$altsource")" "$1" "$2"
}

# GitHub strips custom URL schemes, so iOS links use AltDirect HTTPS redirects.
# Nightly is a separate app/package (eu.depau.loak.nightly), with its own source
# and an Obtainium filter that selects only nightly APKs.
if [[ "$channel" == "nightly" ]]; then
	obtainium="$(obtainium_link "eu.depau.loak.nightly" "Lo'ak Nightly" '^Lo\.ak-nightly-.*\.apk$')"
	altsource='https://github.com/depau/loak/releases/download/nightly/app-repo.json?raw=1'
else
	obtainium="$(obtainium_link "eu.depau.loak" "Lo'ak" '^Lo\.ak\.apk$')"
	altsource='https://raw.githubusercontent.com/depau/loak/main/app-repo.json'
fi
cat <<EOF
$title

<table>
<thead>
<tr><th align="left">Platform</th><th align="left">x86-64</th><th align="left">arm64</th></tr>
</thead>
<tbody>
<tr>
<td><strong>Linux</strong></td>
<td>$(dl "${dp}linux-x86_64.AppImage" "AppImage") · $(dl "${dp}linux-amd64.deb" "DEB") · $(dl "${dp}linux-x86_64.rpm" "RPM") · $(dl "${pp}X64-Linux.tar.gz" "tar.gz")</td>
<td>$(dl "${dp}linux-arm64.AppImage" "AppImage") · $(dl "${dp}linux-arm64.deb" "DEB") · $(dl "${dp}linux-aarch64.rpm" "RPM") · $(dl "${pp}ARM64-Linux.tar.gz" "tar.gz")</td>
</tr>
<tr>
<td><strong>macOS</strong></td>
<td>$(dl "${dp}mac-x64.dmg" "DMG") · $(dl "${dp}mac-x64.zip" "ZIP")</td>
<td>$(dl "${dp}mac-arm64.dmg" "DMG") · $(dl "${dp}mac-arm64.zip" "ZIP")</td>
</tr>
<tr>
<td><strong>Windows</strong></td>
<td>$(dl "${dp}win-x64.exe" "EXE") · $(dl "${dp}win-x64.msi" "MSI") · $(dl "${pp}X64-Windows.zip" "zip")</td>
<td>$(dl "${dp}win-arm64.exe" "EXE") · $(dl "${dp}win-arm64.msi" "MSI") · $(dl "${pp}ARM64-Windows.zip" "zip")</td>
</tr>
<tr>
<td><strong>Android</strong></td>
<td colspan="2">$(dl "$apk" "APK") · Add to: $obtainium</td>
</tr>
<tr>
<td><strong>iOS</strong></td>
<td colspan="2">$(dl "$ipa" "IPA") · Add to: $(altsource_link altstore "AltStore Classic"), $(altsource_link sidestore "SideStore"), $(altsource_link livecontainer "LiveContainer")</td>
</tr>
</tbody>
</table>

Installers auto-update; portable builds (tar.gz/zip) don't. \`.blockmap\` and channel \`.yml\` files are auto-update metadata, not downloads.
EOF

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
#                   Obtainium / AltStore source deep links. Defaults to "stable".
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

# Deep links into the mobile package managers. Nightly is a separate app/package
# (eu.depau.loak.nightly) so it runs alongside the stable one; its Obtainium entry
# already sets includePrereleases=true and the ^Lo\.ak-nightly-.*\.apk$ regex.
if [[ "$channel" == "nightly" ]]; then
	obtainium='<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22eu.depau.loak.nightly%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fdepau%2Floak%22%2C%22author%22%3A%22depau%22%2C%22name%22%3A%22Lo%27ak%20Nightly%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Atrue%2C%5C%22fallbackToOlderReleases%5C%22%3Atrue%2C%5C%22verifyLatestTag%5C%22%3Afalse%2C%5C%22sortMethodChoice%5C%22%3A%5C%22date%5C%22%2C%5C%22versionDetection%5C%22%3Atrue%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22%5ELo%5C%5C%5C%5C.ak-nightly-.%2A%5C%5C%5C%5C.apk%24%5C%22%7D%22%7D">Add to Obtainium</a>'
	altsource='https://github.com/depau/loak/releases/download/nightly/app-repo.json%3Fraw%3D1'
else
	obtainium='<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22eu.depau.loak%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fdepau%2Floak%22%2C%22author%22%3A%22depau%22%2C%22name%22%3A%22Lo%27ak%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Atrue%2C%5C%22fallbackToOlderReleases%5C%22%3Atrue%2C%5C%22verifyLatestTag%5C%22%3Afalse%2C%5C%22sortMethodChoice%5C%22%3A%5C%22date%5C%22%2C%5C%22versionDetection%5C%22%3Atrue%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22%5ELo%5C%5C%5C%5C.ak%5C%5C%5C%5C.apk%24%5C%22%7D%22%7D">Add to Obtainium</a>'
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
<td colspan="2">$(dl "$apk" "APK") · universal</td>
</tr>
<tr>
<td><strong>iOS</strong></td>
<td colspan="2">$(dl "$ipa" "IPA") · universal</td>
</tr>
</tbody>
</table>

### Install on mobile

<strong>Android</strong> — $obtainium (installs and updates the APK from this release automatically).

<strong>iOS</strong> — add the AltSource in <a href="altstore://source?url=$altsource">AltStore Classic</a> or <a href="sidestore://source?url=$altsource">SideStore</a> to install and keep this channel updated.

Installers auto-update; portable builds (tar.gz/zip) don't. \`.blockmap\` and channel \`.yml\` files are auto-update metadata, not downloads.
EOF

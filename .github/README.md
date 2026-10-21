<div align="center">

A modern (Open)Subsonic music streaming app for Android and iOS.

Fork of [Navic](https://github.com/ssalggnikool/Navic).

[![Try it in your browser](https://raw.githubusercontent.com/NavicApp/Branding/refs/heads/main/assets/web.svg)][WEB_DEMO]
[![Add to Obtainium](https://raw.githubusercontent.com/NavicApp/Branding/refs/heads/main/assets/obtainium.svg)][ADD_TO_OBTAINIUM]
[![AltSource provides links for most sideloading apps, like Feather](https://raw.githubusercontent.com/NavicApp/Branding/refs/heads/main/assets/altsource.svg)][ALTSOURCE]
[![Link to the latest release where you can download the APK or IPA directly](https://raw.githubusercontent.com/NavicApp/Branding/refs/heads/main/assets/direct_download.svg)][LATEST_RELEASE]
[![Discord](https://raw.githubusercontent.com/NavicApp/Branding/refs/heads/main/assets/discord.svg)](https://discord.gg/TBcnNX66PH)

</div>

## Features

* **Modern**: actually decent UI (unlike a lot of players) with Material 3 Expressive design
* **Artwork fallbacks**: missing covers show one note (or radio icon) on a contrasting,
  theme-aware tile
* **Song lists**: compact, content-sized rows across search, library and mix previews;
  longer titles can still wrap
* **Highly customisable**: almost everything is a setting!
* **Works everywhere**: access library offline & scrobble offline
* **Powerful playback**: equaliser, ReplayGain\*, transcoding\* & more
* **Queue gestures**: swipe halfway to remove or play next, with threshold haptics and
  spring snap-back; move back before releasing to cancel, or use Undo after committing
* **Trackpad swipes** (macOS): two fingers act as a finger on a touch screen, and swiping
  right goes back, following the fingers
* **Lyrics support**: multiple lyric providers & word-by-word lyrics\*
* **Radio stations**: stream & add internet radio stations
* **Home widgets**: a couple of home screen widgets are inbuilt
* **Share music**: share links to music to your friends
* **Android Auto**: control music playback through Android Auto
* **Android backup**: system backup and device transfer restore app settings
* **Pretty lightweight**: Lo'ak is fast and small (~10MB)
* **Compatible**: supports older devices and most Subsonic
  servers ([LMS](https://github.com/epoupon/lms), [gonic](https://github.com/sentriz/gonic), [Navidrome](https://navidrome.org),
  etc.)

<sub>*may need an OpenSubsonic-compatible server to work fully</sub>

## Screenshots

|                                        Library                                         |                                        Player                                        |                                        Lyrics                                        |                                        Albums                                        |
|:--------------------------------------------------------------------------------------:|:------------------------------------------------------------------------------------:|:------------------------------------------------------------------------------------:|:------------------------------------------------------------------------------------:|
| ![](fastlane/metadata/android/en-US/images/phoneScreenshots/1.jpg) | ![](fastlane/metadata/android/en-US/images/phoneScreenshots/2.jpg) | ![](fastlane/metadata/android/en-US/images/phoneScreenshots/3.jpg) | ![](fastlane/metadata/android/en-US/images/phoneScreenshots/4.jpg) |

## Android

Lo'ak can notify you when a GitHub release is available. Obtainium can instead
install updates automatically; use the matching entry because stable and nightly
are separate apps with different package IDs.

| Channel | Package | Obtainium |
|---|---|---|
| **Stable** | `eu.depau.loak` | [Add][ADD_TO_OBTAINIUM] |
| **Nightly** | `eu.depau.loak.nightly` | [Add][ADD_NIGHTLY_TO_OBTAINIUM] |

When Obtainium or another package manager handles updates, turn off
**Settings → About Lo'ak → Check for updates** to avoid duplicate prompts.

## iOS (AltStore Classic / SideStore / LiveContainer)

iOS builds are distributed through an AltSource; add the source in **AltStore
Classic**, **SideStore**, or **LiveContainer** to install and get updates. If you
use both a stable and a nightly app, add **only one** of each source — the Nightly
is a separate app (`eu.depau.loak.nightly`) so it can run alongside the stable one.

| Source | AltStore Classic | SideStore | LiveContainer |
|---|---|---|---|
| **Stable** (`Loak`) | [Add][ADD_STABLE_TO_ALTSTORE] | [Add][ADD_STABLE_TO_SIDESTORE] | [Add][ADD_STABLE_TO_LIVECONTAINER] |
| **Nightly** (`Loak Nightly`) | [Add][ADD_NIGHTLY_TO_ALTSTORE] | [Add][ADD_NIGHTLY_TO_SIDESTORE] | [Add][ADD_NIGHTLY_TO_LIVECONTAINER] |

<details>
<summary>Manual source URLs</summary>

- Stable: `https://raw.githubusercontent.com/depau/loak/main/app-repo.json`
- Nightly: `https://github.com/depau/loak/releases/download/nightly/app-repo.json?raw=1`

</details>

Apps installed this way are re-signed with your own free Apple ID and expire
after 7 days (refresh from the store keeps them alive). AltStore PAL is not
supported: it requires Apple notarization.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) to see how you can help out!

[WEB_DEMO]: https://depau.github.io/loak

[ADD_TO_OBTAINIUM]: https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22eu.depau.loak%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fdepau%2Floak%22%2C%22author%22%3A%22depau%22%2C%22name%22%3A%22Lo%27ak%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Atrue%2C%5C%22fallbackToOlderReleases%5C%22%3Atrue%2C%5C%22verifyLatestTag%5C%22%3Afalse%2C%5C%22sortMethodChoice%5C%22%3A%5C%22date%5C%22%2C%5C%22versionDetection%5C%22%3Atrue%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22%5ELo%5C%5C%5C%5C.ak%5C%5C%5C%5C.apk%24%5C%22%7D%22%7D

[ADD_NIGHTLY_TO_OBTAINIUM]: https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22eu.depau.loak.nightly%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fdepau%2Floak%22%2C%22author%22%3A%22depau%22%2C%22name%22%3A%22Lo%27ak%20Nightly%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Atrue%2C%5C%22fallbackToOlderReleases%5C%22%3Atrue%2C%5C%22verifyLatestTag%5C%22%3Afalse%2C%5C%22sortMethodChoice%5C%22%3A%5C%22date%5C%22%2C%5C%22versionDetection%5C%22%3Atrue%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22%5ELo%5C%5C%5C%5C.ak-nightly-.%2A%5C%5C%5C%5C.apk%24%5C%22%7D%22%7D

[ALTSOURCE]: #ios-altstore-classic--sidestore--livecontainer

[ADD_STABLE_TO_ALTSTORE]: https://altdirect.app/?url=https%3A%2F%2Fraw.githubusercontent.com%2Fdepau%2Floak%2Fmain%2Fapp-repo.json&r=altstore
[ADD_STABLE_TO_SIDESTORE]: https://altdirect.app/?url=https%3A%2F%2Fraw.githubusercontent.com%2Fdepau%2Floak%2Fmain%2Fapp-repo.json&r=sidestore
[ADD_STABLE_TO_LIVECONTAINER]: https://altdirect.app/?url=https%3A%2F%2Fraw.githubusercontent.com%2Fdepau%2Floak%2Fmain%2Fapp-repo.json&r=livecontainer
[ADD_NIGHTLY_TO_ALTSTORE]: https://altdirect.app/?url=https%3A%2F%2Fgithub.com%2Fdepau%2Floak%2Freleases%2Fdownload%2Fnightly%2Fapp-repo.json%3Fraw%3D1&r=altstore
[ADD_NIGHTLY_TO_SIDESTORE]: https://altdirect.app/?url=https%3A%2F%2Fgithub.com%2Fdepau%2Floak%2Freleases%2Fdownload%2Fnightly%2Fapp-repo.json%3Fraw%3D1&r=sidestore
[ADD_NIGHTLY_TO_LIVECONTAINER]: https://altdirect.app/?url=https%3A%2F%2Fgithub.com%2Fdepau%2Floak%2Freleases%2Fdownload%2Fnightly%2Fapp-repo.json%3Fraw%3D1&r=livecontainer

[LATEST_RELEASE]: https://github.com/depau/loak/releases

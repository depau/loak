package eu.depau.loak.androidApp.di

import eu.depau.loak.androidApp.R
import eu.depau.loak.di.ResourceProvider

class AndroidResourceProvider(
	override val appIconDefault: Int = R.mipmap.ic_launcher,
	override val appIconInverted: Int = R.mipmap.ic_launcher_inverted,
	override val icLoak: Int = R.drawable.ic_loak,
	override val animPlaylist: Int = R.drawable.anim_playlist,
	override val animArtist: Int = R.drawable.anim_artist,
	override val animPause: Int = R.drawable.anim_pause
) : ResourceProvider

#!/usr/bin/env bash
# Regenerates the UI-test music library in ./music: short tagged sine tones,
# one album cover, one synced-lyrics file and one playlist. The output is
# committed, so CI needs no ffmpeg; rerun this only to change the library.
set -euo pipefail
cd "$(dirname "$0")"
rm -rf music
mkdir -p music

# artist|album|year|genre|track titles (comma separated)
albums=(
	"Aurora Test|First Light|2019|Ambient|Morning Static,Low Tide,Paper Lanterns"
	"Aurora Test|Second Wind|2021|Ambient|Northbound,Glass Harbor,Quiet Engine"
	"Basalt Echo|Third Rail|2018|Electronic|Signal Fire,Copper Lines,Night Ferry"
	"Cinder Vale|Fourth Wall|2023|Rock|Open Window,Last Orbit,Slow Burn"
)

freq=220
for entry in "${albums[@]}"; do
	IFS='|' read -r artist album year genre titles <<<"$entry"
	dir="music/$artist/$album"
	mkdir -p "$dir"
	IFS=',' read -r -a tracks <<<"$titles"
	n=1
	for title in "${tracks[@]}"; do
		ffmpeg -loglevel error -f lavfi -i "sine=frequency=$freq:duration=10" \
			-ac 1 -ar 22050 -b:a 16k \
			-metadata title="$title" -metadata artist="$artist" -metadata album_artist="$artist" \
			-metadata album="$album" -metadata date="$year" -metadata genre="$genre" \
			-metadata track="$n/${#tracks[@]}" \
			"$dir/$(printf '%02d' "$n") $title.mp3"
		n=$((n + 1))
		freq=$((freq + 55))
	done
done

ffmpeg -loglevel error -f lavfi -i "color=c=0x5b6ee1:s=300x300" -frames:v 1 \
	"music/Aurora Test/First Light/cover.jpg"

cat >"music/Aurora Test/First Light/01 Morning Static.lrc" <<'EOF'
[00:00.00]First line of the test lyrics
[00:03.00]Second line arrives
[00:06.00]Third line holds
[00:09.00]Last line fades
EOF

cat >"music/UI Test Mix.m3u" <<'EOF'
#EXTM3U
Aurora Test/First Light/01 Morning Static.mp3
Basalt Echo/Third Rail/02 Copper Lines.mp3
Cinder Vale/Fourth Wall/03 Slow Burn.mp3
EOF

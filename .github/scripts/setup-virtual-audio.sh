#!/usr/bin/env bash
# A virtual audio output for the UI tests on Linux runners, which have no sound card:
# PipeWire with a null sink, reachable through ALSA (the desktop app's rodio) and
# PulseAudio (browsers). Daemons stay up for the rest of the job.
set -euo pipefail

sudo apt-get update -qq
sudo apt-get install -y -qq --no-install-recommends \
	pipewire pipewire-pulse pipewire-alsa wireplumber pulseaudio-utils dbus-x11

export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/run/user/$(id -u)}"
if [ ! -d "$XDG_RUNTIME_DIR" ]; then
	sudo mkdir -p "$XDG_RUNTIME_DIR"
	sudo chown "$(id -u)" "$XDG_RUNTIME_DIR"
	chmod 700 "$XDG_RUNTIME_DIR"
fi
echo "XDG_RUNTIME_DIR=$XDG_RUNTIME_DIR" >> "$GITHUB_ENV"

eval "$(dbus-launch --sh-syntax)"
for daemon in pipewire wireplumber pipewire-pulse; do
	nohup "$daemon" > "/tmp/$daemon.log" 2>&1 &
	sleep 1
done
for _ in $(seq 20); do pactl info > /dev/null 2>&1 && break; sleep 0.5; done

pactl load-module module-null-sink sink_name=ci_null > /dev/null
pactl set-default-sink ci_null
pactl info | grep "Default Sink"

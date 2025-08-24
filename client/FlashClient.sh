#!/usr/bin/env bash

ver_url="https://raw.githubusercontent.com/GlennnM/FlashPrivateServer/refs/heads/main/version.txt"
ver=$(curl -fsSL "$ver_url" | tr -d '\r\n[:space:]')

if [ -z "$ver" ]; then
    echo "Could not detect version. Likely a network error." >&2
    exit 1
fi

echo "Detected version: $ver"

if [ "$ver" != "4.0" ]; then
    echo "Version does not match latest. Update at https://github.com/GlennnM/FlashPrivateServer" >&2
    exit 1
fi

paths=(
    "$HOME/Library/Application Support/Steam/steamapps/common/Ninja Kiwi Archive/resources"
    "/Applications/Ninja Kiwi Archive"*".app/Contents/Resources"
    "$HOME/.steam/steam/steamapps/common/Ninja Kiwi Archive/resources"
    "$HOME/.local/share/Steam/steamapps/common/Ninja Kiwi Archive/resources"
    "$HOME/.steam/steam/steamapps/compatdata/1275350/pfx/drive_c/Program Files (x86)/Steam/steamapps/common/Ninja Kiwi Archive/resources"
    "$HOME/.var/app/com.valvesoftware.Steam/.steam/steam/steamapps/common/Ninja Kiwi Archive/resources"
)

curl -fsSLO "https://github.com/GlennnM/FlashPrivateServer/releases/download/v4.0/app.zip"
count_extracted=0
found_install=0

for p in "${paths[@]}"; do
    if [ -d "$p" ]; then
        found_install=1
        echo "Found Ninja Kiwi Archive installation at: $p"
        rm -vf "$p/appv.asar"
        mv -vf "$p/app.asar" "$p/appv.asar"
        unzip app.zip -d "$p" &&
        ((count_extracted++))
    fi
done

if [ "$found_install" -eq 0 ]; then
    echo "Could not find Ninja Kiwi Archive installation" >&2
    exit 1
fi

echo "Installed $count_extracted files"

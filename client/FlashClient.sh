#!/bin/bash
url="https://github.com/GlennnM/FlashPrivateServer/raw/main/version.txt"
ver=$(curl -s "$URL")
if [ "$ver" != "4.0" ]; then
	echo "Out of date, or no connection, Update at https://github.com/GlennnM/FlashPrivateServer"
fi
paths=(
"$HOME/Library/Application Support/Steam/steamapps/common/Ninja Kiwi Archive/resources"
"/Applications/Ninja Kiwi Archive.app/Contents/Resources"
"$HOME/.steam/steam/steamapps/common/Ninja Kiwi Archive"
"$HOME/.local/share/Steam/steamapps/common/Ninja Kiwi Archive"
"$HOME/.steam/steam/steamapps/compatdata/1275350/pfx/drive_c/Program Files (x86)/Steam/steamapps/common/Ninja Kiwi Archive"
)
count_extracted=0
curl -sS -L https://github.com/GlennnM/FlashPrivateServer/releases/download/v4.0/app.zip > app.zip
for p in "${paths[@]}"; do
  if [ -d "$p" ]; then
    echo "Directory exists: $p"
	rm "$p/app.asar"
    unzip app.zip -d "$p" &&
    ((count_extracted++))
  else
    echo "Directory does NOT exist: $p"
  fi
done

echo "Total installations: $count_extracted"

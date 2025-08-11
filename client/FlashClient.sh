#!/bin/bash
url="https://raw.githubusercontent.com/GlennnM/FlashPrivateServer/refs/heads/main/version.txt"
ver=$(curl -s "$url" | tr -d '\r\n[:space:]') 
echo "$ver"
if [ "$ver" != "4.0" ]; then
	echo "Out of date, or no connection, Update at https://github.com/GlennnM/FlashPrivateServer"
fi
paths=(
"$HOME/Library/Application Support/Steam/steamapps/common/Ninja Kiwi Archive/resources"
"/Applications/Ninja Kiwi Archive"*".app/Contents/Resources"
"$HOME/.steam/steam/steamapps/common/Ninja Kiwi Archive/resources"
"$HOME/.local/share/Steam/steamapps/common/Ninja Kiwi Archive/resources"
"$HOME/.steam/steam/steamapps/compatdata/1275350/pfx/drive_c/Program Files (x86)/Steam/steamapps/common/Ninja Kiwi Archive/resources"
)
count_extracted=0
curl -sS -L "https://github.com/GlennnM/FlashPrivateServer/releases/download/v4.0/app.zip" > app.zip
for p in "${paths[@]}"; do
  if [ -d "$p" ]; then
    echo "Directory exists: $p"
	rm "$p/appv.asar"
	mv "$p/app.asar" "$p/appv.asar"
    unzip app.zip -d "$p" &&
    ((count_extracted++))
  else
    echo "Directory does NOT exist: $p"
  fi
done

echo "Total installations: $count_extracted"

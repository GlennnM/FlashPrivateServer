#!/bin/sh
curl https://github.com/GlennnM/FlashPrivateServer/raw/main/v3.8.1.txt > /dev/null && \
cd "$HOME/Library/Application Support/Ninja Kiwi Archive/Cache" && \
rm -r * && \
curl -sS -L https://github.com/GlennnM/FlashPrivateServer/releases/download/v3.8.1/cache_osx.zip > file.zip && \
unzip file.zip && \
rm file.zip && \
echo "Installation successful!"

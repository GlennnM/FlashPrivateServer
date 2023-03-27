cd ../
javac ./src/java/xyz/hydar/net/*.java ./src/java/xyz/hydar/flash/util/*.java ./src/java/xyz/hydar/flash/mp/*.java -cp ./classes -d ./classes --release 19 --enable-preview
cd ./classes/
jar -cfe ../FlashServer.jar xyz.hydar.flash.FlashLauncher *
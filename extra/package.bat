cd ../
javac ./src/java/xyz/hydar/net/*.java ./src/java/xyz/hydar/flash/util/*.java ./src/java/xyz/hydar/flash/*.java -cp ./classes -d ./classes
cd ./classes/
jar -cfe ../FlashServer.jar xyz.hydar.flash.FlashLauncher *
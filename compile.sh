javac ./src/java/xyz/hydar/net/*.java ./src/java/xyz/hydar/flash/util/*.java ./src/java/xyz/hydar/flash/*.java -cp ./classes -d ./classes
java -cp ./classes xyz.hydar.flash.FlashLauncher "$@"

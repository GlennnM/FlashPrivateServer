# NKFlashServers
On April 29, 2022, Ninja Kiwi shut down their multiplayer servers for the following games:<br>
<br>
SAS4 Flash<br>
Countersnipe Flash<br>
SAS3 Flash<br>
BTD5 Flash<br>
as well as BTD Battles Flash, despite it not being mentioned in the original blog post.<br>
<br>
This project fully recreates these servers, built from scratch with Java TCP sockets.<br>
Currently SAS4 and BTD Battles are being hosted. The guide to play on these servers is also available in our discord and on youtube.<br>
When playing on these servers, some quality of life/easter egg features are added, but mostly they will play exactly as you would expect the game to play on Ninja Kiwi's servers, and any data or achievements from these games will register in your actual NK profile.<br>
<h1>
INSTALLATION GUIDE<br></h1>
<h2>First time setup:</h2><br>
 1. Install <a href = https://www.telerik.com/download/fiddler>Fiddler Classic</a> and open it.<br>
 2. On the menu bar, select Tools->Options.
  <img src = https://user-images.githubusercontent.com/77253453/174670742-faf229c2-3673-467a-85a0-04f4f1414fbc.png><br>
 3. Select the "HTTPS" tab. Enable Decrypt HTTPS traffic. Choose "from non-browsers only", since decrypting HTTPS from your browser can cause issues on many sites<br>(but consider trying "from all processes" if you have issues later and/or want to play in a browser)<br>
 <img src = https://user-images.githubusercontent.com/77253453/174671525-88d40c45-a6e9-4cdc-b72b-c36996e2ca79.png>
<br>
4. Click "OK" to close the options menu, then click the "AutoResponder" button on the right side of the interface.<br>
5. Check "Enable Rules" and "Unmatched requests passthrough", then click "Add Rule".<br>
<img src = https://user-images.githubusercontent.com/77253453/174673035-4d78c2dd-6cce-4c12-9421-497c565243d8.png><br>
<br>
6. Follow the instructions below depending on which game(s) you want to play, then proceed to step 7:<br>
<b>SAS4</b><br>
Paste the following line into the top text box:<br>
regex:^http(s|)://assets.nkstatic.com/Games/gameswfs/sas4/sas4.swf<br>
and the following in the bottom text box:<br>
https://github.com/GlennnM/NKFlashServers/raw/main/SAS4/sas4.swf<br>
Click "Save".
<img src = https://user-images.githubusercontent.com/77253453/174674145-5803dc9e-0eb7-4be2-a7fd-21649fcf8d96.png>

<b>BTD Battles</b><br>
Click add rule again if you already added SAS4 - playing multiple is fine.<br>
Paste the following line into the top text box:<br>
regex:^http(s|)://assets.ninjakiwi.com/Games/gameswfs/btdbattles-dat.swf<br>
and the following in the bottom text box:<br>
https://github.com/GlennnM/NKFlashServers/raw/main/BattlesFlash/btdbattles-dat.swf<br>
Click "Save".<br>
Here is an example of what your screen should look like, if you added both games:<br>
<img src=https://user-images.githubusercontent.com/77253453/174676318-8b5522ee-78c4-45d2-abd2-2e726674a55b.png>

7. Close all NK Archive windows and games. Hold the windows key and press R(Win+R) and paste the following:<br>
<ul>%appdata%/Ninja Kiwi Archive/Cache</ul><br>
then press Run.<br>
<img src = https://user-images.githubusercontent.com/77253453/174675149-f9107ddd-d9b0-4592-bff0-57db6c5b67ac.png>
<br>
8. Clear out this folder by clicking on any of the files that appear, press ctrl+A(Select all), then Shift+Delete(Permanently delete).<br> This will ensure the game updates to use the server, instead of using an old cached version.<br>
<img src = https://user-images.githubusercontent.com/77253453/174674847-2357b7d9-bdca-4378-9db8-b5af0d94e7cf.png>
<br>
9. Start the Ninja Kiwi Archive and then the game you wish to play!<br> To verify you are connected to the server, just join any lobby. If you don't see a blank screen(sas4) or "Connecting to server..." forever(battles) it worked!
<br>
<h2>
Second time - after setup<br>
</h2>
Just ensure you have fiddler open before starting the game. <br>Your fiddler configurations will be saved so no need to change them again, but if you accidentally start it without fiddler open just start fiddler and repeat steps 7-9.<br>
<b>Enjoy!!!</b><br>
<h1>
Building from Source<br>
  </h1>
<br>
Compile the java file or the game you wish to host and run it with any java compiler(10+ might be needed); all the classes are included.<br>
Command line arguments(BTD Battles): 4480<br>
Command line arguments(SAS4): 8124<br>
You can now play locally with one of the "localhost" SWFs. <br>
If you want to host it yourself you will need to change the IP and make a new swf(instructions not provided here, but it is fairly simple)<br>

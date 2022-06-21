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
Currently I am hosting BTD Battles and SAS4. The guide to play on these servers is also available as a video<br>
When playing on these servers, some quality of life/easter egg features are added, but mostly they will play exactly as you would expect the game to play on Ninja Kiwi's servers, and any data or achievements from these games will register in your actual NK profile.<br>
<h1>
HOW TO PLAY<br></h1>
<h2>First time setup:</h2><br>
 1. Install <a href = https://www.telerik.com/download/fiddler>Fiddler Classic</a> and open it.<br>
 2. On the menu bar, select Tools->Options.
  <img src = https://user-images.githubusercontent.com/77253453/174670742-faf229c2-3673-467a-85a0-04f4f1414fbc.png><br>
 3. Select the "HTTPS" tab. Enable Decrypt HTTPS traffic. Choose "from non-browsers only", since decrypting HTTPS from your browser can cause issues on many sites<br>(but consider trying "from all processes" if you have issues later and/or want to play in a browser)<br>Also install whatever certificates it asks you to(a few confirmation dialogs should appear).<br>
 See below step 9 for instructions if you get a "unable to configure windows to trust the fiddler root certificate" error.
  <br>
 <img src = https://user-images.githubusercontent.com/77253453/174671525-88d40c45-a6e9-4cdc-b72b-c36996e2ca79.png>
<br>
4. Click "OK" to close the options menu, then click the "AutoResponder" button on the right side of the interface.<br>
5. Check "Enable Rules" and "Unmatched requests passthrough", then click "Add Rule".<br>
<img src = https://user-images.githubusercontent.com/77253453/174673035-4d78c2dd-6cce-4c12-9421-497c565243d8.png><br>
<br>
6. Follow the instructions below depending on which game(s) you want to play, then proceed to step 7:<br>
<b>SAS4</b><br>
Paste the following line into the top text box:<br>
<code>regex:^http(s|)://assets.nkstatic.com/Games/gameswfs/sas4/sas4.swf</code><br>
and the following in the bottom text box:<br>
<code>https://cdn.jsdelivr.net/gh/GlennnM/NKFlashServers/SAS4/sas4.swf</code><br>
Click "Save".
<img src =https://user-images.githubusercontent.com/77253453/174720990-cb685426-e353-4aad-af80-155e6c2765de.png>

<b>BTD Battles</b><br>
Click add rule again if you already added SAS4 - playing multiple is fine.<br>
Paste the following line into the top text box:<br>
<code>regex:^http(s|)://assets.ninjakiwi.com/Games/gameswfs/btdbattles-dat.swf</code><br>
and the following in the bottom text box:<br>
<code>https://cdn.jsdelivr.net/gh/GlennnM/NKFlashServers/BattlesFlash/btdbattles-dat.swf</code><br>
Click "Save".<br>
Here is an example of what your screen should look like, if you added both games:<br>
<img src = https://user-images.githubusercontent.com/77253453/174679510-29f708a7-6578-443b-ac1b-362c394e74b9.png>

7. Close all NK Archive windows and games. Hold the windows key and press R(Win+R) and paste the following:<br>
<u>%appdata%/Ninja Kiwi Archive/Cache</u><br>
then press OK.<br>
<img src = https://user-images.githubusercontent.com/77253453/174675149-f9107ddd-d9b0-4592-bff0-57db6c5b67ac.png>
<br>
8. Clear out this folder by clicking on any of the files that appear, press ctrl+A(Select all), then Shift+Delete(Permanently delete).<br> This will ensure the game updates to use the server, instead of using an old cached version.<br>
<img src = https://user-images.githubusercontent.com/77253453/174674847-2357b7d9-bdca-4378-9db8-b5af0d94e7cf.png>
<br>
9. Start the Ninja Kiwi Archive and then the game you wish to play!<br> To verify you are connected to the server, just join any lobby. If you don't see a blank screen(sas4) or "Connecting to server..." forever(battles) it worked!<br>
<i>Step 3 help/workaround(ignore if it worked):</i>
<br>If you see this error:<br>
<img src = https://cdn.discordapp.com/attachments/988564906351669268/988593108860162058/unknown.png>
<br>
<details><summary>
follow these steps if you saw the error above:<br>
 </summary>
1. Open Fiddler, go to Tools -> Options -> HTTPS -> Actions -> Export Root Certificate to Desktop<br>
2. Double click the certificate<br>
3. Select if you want to install the certificate in the user store or the machine one<br>
4. Select "Place all certificates in the following store"<br>
5. Click on "Browse..." and select "Trusted Root Certification Authorities"<br>
6. Click Next and Finish<br>
7. New dialog with "The import was successful" message should appear<br>
 </details>
<br>
<h2>
Second time - after setup<br>
</h2>
Just ensure you have fiddler open before starting the game. <br>Your fiddler configurations will be saved so no need to change them again, but if you accidentally start it without fiddler open just start fiddler and repeat steps 7-9.<br>
<b>Enjoy!!!</b><br>
<details><summary>some info about security</summary>
yes i am asking you to click trust on something that says "do not trust". This message is correct in that there are security risks in running fiddler with HTTPS decryption on, which is why we restrict it to non browser applications. basically, anyone who controls a device between your computer and the internet would be able to decrypt your traffic from those applications.

so basically: if you are about to make a sensitive transaction on public wifi, turn off https decryption, and also click the padlock next to url to make sure you aren't on a connection that is "verified by DO_NOT_TRUST"
 </details>
<h1>
Building from Source<br>
  </h1>
<br>
Compile the java file or the game you wish to host and run it with any java compiler(10+ might be needed); all the classes are included.<br>
Command line arguments(BTD Battles): 4480<br>
Command line arguments(SAS4): 8124<br>
You can now play locally with one of the "localhost" SWFs. <br>
If you want to host it yourself you will need to change the IP and make a new swf(instructions not provided here, but it is fairly simple)<br>
<br>


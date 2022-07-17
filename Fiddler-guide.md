# NKFlashServers
<h1>Fiddler Guide<br></h1>
Fiddler allows you to replace the SWFs as they are being loaded. This allows for:<br>
1. multiple archive instances connected to the server<br>
2. browser clients connected to the server<br>
3. self hosting/local servers(note that a separate powershell script for localhost swfs is also available)<br>
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
and the SWF link in the bottom text box:<br>
<code>https://cdn.jsdelivr.net/gh/GlennnM/NKFlashServers/SAS4/sas5.swf</code><br>
Click "Save".
<img src =https://user-images.githubusercontent.com/77253453/174720990-cb685426-e353-4aad-af80-155e6c2765de.png>

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
  accepting the fiddler certificate allows MITM attacks but the private keys to the certificates are unique to each fiddler installation, so an attacker would need access to your computer.
 </details>


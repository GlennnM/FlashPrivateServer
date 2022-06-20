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
 1. Install <a href = https://www.telerik.com/fiddler>Fiddler</a> and open it.<br>
 2. On the menu bar, select Tools->Options.
  <img src = https://user-images.githubusercontent.com/77253453/174670742-faf229c2-3673-467a-85a0-04f4f1414fbc.png><br>
 3. Select the "HTTPS" tab. Enable Decrypt HTTPS traffic. Choose "from non-browsers only", since decrypting HTTPS from your browser can cause issues on many sites<br>(but consider trying "from all processes" if you have issues later and/or want to play in a browser)
 4. ![image](https://user-images.githubusercontent.com/77253453/174671525-88d40c45-a6e9-4cdc-b72b-c36996e2ca79.png)

...<br>
<h1>
Building from Source<br>
  </h1>
<br>
Compile the java file or the game you wish to host and run it with any java compiler(10+ might be needed); all the classes are included.<br>
Command line arguments(BTD Battles): 4480<br>
Command line arguments(SAS4): 8124<br>
You can now play locally with one of the "localhost" SWFs. <br>
If you want to host it yourself you will need to change the IP and make a new swf(instructions not provided here, but it is fairly simple)<br>

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
Currently I am hosting BTD Battles, BTD5 challenges, BTD5 co-op, and SAS4. The guide to play on these servers is also available as a video<br>
When playing on these servers, some quality of life/easter egg features are added, but mostly they will play exactly as you would expect the game to play on Ninja Kiwi's servers, and any data or achievements from these games will register in your actual NK profile.<br>
<h1>
HOW TO PLAY<br></h1>
1. Ensure you have Ninja Kiwi Archive installed(from ninja kiwi website or Steam)<br>
2. Download FlashClient.ps1 from this page<a href = https://github.com/GlennnM/NKFlashServers/releases/tag/v2.0>(click)</a><br>
3. Open the folder you downloaded it to, close all NK Archive windows(INCLUDING THE LAUNCHER), then run the script.<br>
There are different methods of running the PowerShell script. The methods are as follows:<br>

### First Method

1) open [PowerShell](https://docs.microsoft.com/en-us/powershell/scripting/overview?view=powershell-5.1) (or [PowerShell ISE](https://docs.microsoft.com/en-us/powershell/scripting/windows-powershell/ise/introducing-the-windows-powershell-ise?view=powershell-7)) as an Administrator
2) Enable PowerShell execution
<code>Set-ExecutionPolicy Unrestricted -Force</code>
3) On the prompt, change to the directory where you extracted the files:
  `cd c:\Users\NAME_HERE\Downloads`
4) Next, to run the script, enter in the following:
  `.\FlashClient.ps1`

### Second Method

1) Right-click the PowerShell file that you'd like to run and click on "Run With PowerShell"
2) This will allow the script to run without having to do the above steps but Powershell will ask if you're sure you want to run this script.
That's it! Next time you start BTD Battles, BTD5, or SAS4 on the archive they will be modded to link to the private server, allowing you to play.<br><b>Enjoy!!</b><br>
<br>Since the games are fairly inactive, you can play "solo" multiplayer with the following methods:<br>
join code 400 in SAS4 to play a boosted game with bots(works in any mode)<br>
<a href = https://github.com/Kinnay/Bloons-Terminator>bots</a> for battles flash(will require some code changes to work)<br>
<h1>
Hosting your own server from source<br>
  </h1>
Install java 10+ and run the .sh or .bat file for the game you wish to host after replacing the IP in config.txt with your ip(or localhost).<br>
For hosting daily challenges, the "BTD5" folder should be the root directory of a jsp server on the port being accessed by the game. The one being deployed currently is custom and the source is not available here yet.<br>
You can now play locally with one of the "localhost" SWFs<br> To load these, use the <a href=https://github.com/GlennnM/NKFlashServers/blob/main/FlashClient_localhost.ps1>localhost powershell script</a> or the <a href = https://github.com/GlennnM/NKFlashServers/blob/main/Fiddler-guide.md>fiddler guide</a>. <br>
If you want to host it for other people to play you will need to change the IP and make a new swf(instructions not provided here, but it is fairly simple)<br>
<br>


# FlashPrivateServer
On April 29, 2022, Ninja Kiwi shut down their multiplayer servers for the following games:<br>
<br>
SAS4 Flash<br>
Countersnipe Flash<br>
SAS3 Flash<br>
BTD5 Flash<br>
as well as BTD Battles Flash, despite it not being mentioned in the original blog post.<br>
<br>
This project fully recreates these servers, built from scratch with Java TCP sockets.<br>
Currently I am hosting all of these - BTD Battles, BTD5 challenges, BTD5 co-op, SAS3, Countersnipe, and SAS4. The guide to play on these servers is also available as a video(soon<br>
When playing on these servers, some quality of life/easter egg features are added, but mostly they will play exactly as you would expect the game to play on Ninja Kiwi's servers, and any data or achievements from these games will register in your actual NK profile.<br>
<h1>
HOW TO PLAY<br></h1>
1. Ensure you have Ninja Kiwi Archive installed(from ninja kiwi website or Steam)<br>
2. Download FlashClient.ps1 from this page<a href = https://github.com/GlennnM/FlashPrivateServer/releases/tag/v3.2>(click)</a><br>
3. Open the folder you downloaded it to, close all NK Archive windows(INCLUDING THE LAUNCHER), then run the script.<br>
There are different methods of running the PowerShell script. The methods are as follows:<br>

### First Method

1) open [PowerShell](https://docs.microsoft.com/en-us/powershell/scripting/overview?view=powershell-5.1) (or [PowerShell ISE](https://docs.microsoft.com/en-us/powershell/scripting/windows-powershell/ise/introducing-the-windows-powershell-ise?view=powershell-7))
2) Enable PowerShell execution
<code>Set-ExecutionPolicy Unrestricted -Force</code>
3) On the prompt, change to the directory where you downloaded the files:
  `cd c:\Users\NAME_HERE\Downloads`
4) Next, to run the script, enter in the following:
  `.\FlashClient.ps1`

### Second Method

1) Right-click the PowerShell file that you'd like to run and click on "Run With PowerShell"
2) This will allow the script to run without having to do the above steps but Powershell will ask if you're sure you want to run this script.


<br>That's it! Next time you start BTD Battles, BTD5, SAS3, Countersnipe, or SAS4 on the archive they will be modded to link to the private server, allowing you to play.<br><b>Enjoy!!</b><br>
<br>Since the games are fairly inactive, you can play "solo" multiplayer with the following methods:<br>
join code 400 in SAS4 to play a boosted game with bots(works in any mode)<br>
<a href = https://github.com/Kinnay/Bloons-Terminator>bots</a> for battles flash(will require some code changes to work)<br><br>
If you would like to run multiple instances of the same game, opening multiple archive launchers will not work with these mods.<br>
However, you can run them from the same launcher using the following mod:<br>
<a href=https://github.com/GlennnM/NKMultiArchive>NKMultiArchive</a><br>
<details>
<summary>
Stuck installing flash player?<br>
</summary><br>
If the links provided to you during archive installation don't work for you(make sure to read 'How to Play' first!) try one of the following methods:<br><br>
&nbsp;&nbsp;&nbsp;&nbsp;<details><summary><h2>Archive.org installer - requires admin<br></h2></summary> <a href=https://archive.org/download/flashplayerarchivedversions2/333/fp_29.0.0.171_archive.zip>https://archive.org/download/flashplayerarchivedversions2/333/fp_29.0.0.171_archive.zip</a><br>
&nbsp;&nbsp;&nbsp;&nbsp;1. extract the zip file from the link above<br>
&nbsp;&nbsp;&nbsp;&nbsp;2. run the correct installer(most likely winpep something)<br>
&nbsp;&nbsp;&nbsp;&nbsp;3. Restart the archive and it should load!<br></details>
&nbsp;&nbsp;&nbsp;&nbsp;<details><summary><h2>Manual 'install' - no admin required</h2><br></summary>
&nbsp;&nbsp;&nbsp;&nbsp;1. download "pepflashplayer.dll" for your system(just search for it on google)<br>
<details>
    &nbsp;&nbsp;&nbsp;&nbsp;<summary>How to verify a .dll from the internet is legit<br></summary>
    &nbsp;&nbsp;&nbsp;&nbsp;1. right click on pepflashplayer.dll(the one extracted from the zip, not the zip itself) and click 'Properties'<br>
    &nbsp;&nbsp;&nbsp;&nbsp;2. click the 'Digital Signatures' tab(if it isn't there don't trust the file)<br>
    &nbsp;&nbsp;&nbsp;&nbsp;3. click on the signature by 'Adobe Systens Incorporated'(if it isn't there don't trust the file)<br>
    &nbsp;&nbsp;&nbsp;&nbsp;4. click 'Details', then 'Advanced'<br>
    &nbsp;&nbsp;&nbsp;&nbsp;5. verify that the 'Issuer' is something other than the 'Name of signer' in the original signature list. For instance:<br>
    &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;-Name of signer: Adobe Systems Incorporated<br>
    &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;-Issuer: DigiCert EV Code Signing CA (SHA2)<br>
    &nbsp;&nbsp;&nbsp;&nbsp;However, if both say "Adobe Systems Incorporated" then it is a self signed certificate and you shouldn't trust the file.<br>
    
</details>
&nbsp;&nbsp;&nbsp;&nbsp;2. navigate to %appdata%/Ninja Kiwi Archive/ in file explorer<br>
&nbsp;&nbsp;&nbsp;&nbsp;3. open the folder there named "flash"(create it if it didn't exist)<br>
&nbsp;&nbsp;&nbsp;&nbsp;4. delete anything there previously<br>
&nbsp;&nbsp;&nbsp;&nbsp;5. create a folder called "system"<br>
&nbsp;&nbsp;&nbsp;&nbsp;6. paste the pepflashplayer.dll there<br>
&nbsp;&nbsp;&nbsp;&nbsp;7. restart the archive and it should load!<br>
</details></details>
<h1>
Building<br>
  </h1>
<h2>With OpenJDK 19:</h2><br>
This is required for the newest version(the tree starting from src/java), for virtual threads.<br><br>
1. Install OpenJDK 19 and add its binaries to your PATH environment variable(if you don't want to, edit the scripts to absolute paths).<br>
2. Download the source and run "compile.bat" or "compile.sh" depending on your system.<br><br>
This will compile the source to ./classes and launch the server. <br>
To configure the server, edit "flash.properties".<br>
You can also create a jar file with <code>mvn package</code> or <code>extra/package.bat</code> (make sure to run it with --enable-preview), or download one from build artifacts.<br>

<h2>With JDK 8-18:</h2><br>
This version is a few updates behind but each server is only a single source file, and they have been tested to run consistently for months.<br><br>
1. Add the JDK to your system PATH, or edit the script to an absolute one in the next step.<br>
2. Navigate to extra/old/[game name] and run "runme.bat" or "runme.sh" depending on your system.<br>
<br>
<br>
There are no dependencies for either version.<br>
<h2>Daily Challenges</h2>
These are hosted on a JSP servlet, which can be built from src/webapp.<br>
<h2>Client</h2>
In order to play on a server you are hosting, you will have to create SWFs that link game clients to your server,<br>
since the ones generated by the powershell scripts only link to my server.<br>
This can be done through decompilation+Fiddler MITM and other methods, but eventually an archive mod with server selection might be available.<br>
Some useful tools for analyzing client and server behavior are <a href=https://github.com/jindrapetrik/jpexs-decompiler>FFDec</a> and <a href=https://www.wireshark.org/download.html>Wireshark</a>.
<br>
<h1>
Contact<br>
</h1>
If you have questions or concerns feel free to message me on discord: Glenn M#9606

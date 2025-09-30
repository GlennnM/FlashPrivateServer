# FlashPrivateServer
On April 29, 2022, Ninja Kiwi shut down their multiplayer servers for the following games:<br>
- SAS4 Flash
- Countersnipe Flash
- SAS3 Flash
- BTD5 Flash
- BTD Battles Flash

**2025 update** - Bloons Monkey City has been added as well. It will become launchable from the Archive if you install this mod.
This project fully recreates these servers, built from scratch with Java TCP sockets.<br>
Currently I am hosting all of these - BMC, BTD Battles, BTD5 challenges, BTD5 co-op, SAS3, Countersnipe, and SAS4. The guide to play on these servers is also available as a video: <br>https://www.youtube.com/watch?v=J3q-Vb5A4jI<br>
When playing on these servers, some quality of life/easter egg features are added to the client, but mostly they will play exactly as you would expect the game to play on Ninja Kiwi's servers, and any data or achievements from these games will register in your actual NK profile, excluding BMC where you will get a new online save.<br>

# HOW TO PLAY
There are different methods of installing the client. 

In all cases, ensure Ninja Kiwi Archive is installed, and **close all NK Archive windows** before installing!

### First Method - All Platforms

1) Download <code>FlashClient.bat</code>(windows) or <code>FlashClient.sh</code>(Mac or Linux) from [the releases page](https://github.com/GlennnM/FlashPrivateServer/releases/latest) and run it. You might receive a warning since the application isn't signed.


### Second Method - All Platforms (Manual Installation)

1) Navigate to your NK Archive installation folder. Steam example: open Library, right click NK Archive, Manage -> Browse local files.
2) Open the "resources" folder, and delete or rename <code>app.asar</code>.
3) Download <code>app.zip</code> from [the releases page](https://github.com/GlennnM/FlashPrivateServer/releases/latest) and extract it in the current folder(ending in "resources") to get a new <code>app.asar</code>.
<details> 
  <summary><ins>Mac/Linux/Standalone Paths:</ins></summary>
 - Standalone Windows - Current User <code>"%LOCALAPPDATA%\Programs\Ninja Kiwi Archive\resources"</code><br>
 - Standalone Windows - All Users <code>"%PROGRAMFILES%\Ninja Kiwi\Ninja Kiwi Archive\resources"</code><br>
 - Mac Steam <code>"$HOME/Library/Application Support/Steam/steamapps/common/Ninja Kiwi Archive/resources"</code><br>
 - Mac Standalone <code>"/Applications/Ninja Kiwi Archive"*".app/Contents/Resources"</code><br>
 - Linux Steam <code>"$HOME/.steam/steam/steamapps/common/Ninja Kiwi Archive/resources"</code><br>
 - Linux Steam 2 <code>"$HOME/.local/share/Steam/steamapps/common/Ninja Kiwi Archive/resources"</code><br>
 - Linux Proton <code>"$HOME/.steam/steam/steamapps/compatdata/1275350/pfx/drive_c/Program Files (x86)/Steam/steamapps/common/Ninja Kiwi Archive/resources"</code><br>
 - Linux Flatpak <code>"$HOME/.var/app/com.valvesoftware.Steam/.steam/steam/steamapps/common/Ninja Kiwi Archive/resources"</code><br>
  </details>
  
### Third Method - All Platforms (PowerShell)

1) Download <code>FlashClient.ps1</code> from [the releases page](https://github.com/GlennnM/FlashPrivateServer/releases/latest)
2) open [PowerShell](https://docs.microsoft.com/en-us/powershell/scripting/overview?view=powershell-5.1) (or [PowerShell ISE](https://docs.microsoft.com/en-us/powershell/scripting/windows-powershell/ise/introducing-the-windows-powershell-ise?view=powershell-7))
3) Enable PowerShell execution
<code>Set-ExecutionPolicy Unrestricted -Force</code>
4) On the prompt, change to the directory where you downloaded the files:
  `cd c:\Users\NAME_HERE\Downloads`
5) Next, to run the script, enter in the following:
  `.\FlashClient.ps1`


<br>That's it! Next time you start BMC, BTD Battles, BTD5, SAS3, Countersnipe, or SAS4 on the archive they will be modded to link to the private server, allowing you to play online with other players.<br><b>Enjoy!!</b><br>
<br>Since the games are fairly inactive, you can play "solo" multiplayer with the following methods:<br>
- open multiple NK Archives(less lag), or multiple of the same game from the same Archive<br>
- join code 400 in SAS4 to play a boosted game with bots(works in any mode)<br>
- <a href = https://github.com/Kinnay/Bloons-Terminator>bots</a> for battles flash(will require some code changes to work)<br><br>

### Stuck installing flash player?
If the links provided to you during archive installation don't work for you(make sure to read 'How to Play' first!) try one of the following methods:
&nbsp;&nbsp;&nbsp;&nbsp;<details><summary><ins>Archive.org installer - requires admin</ins></summary> <a href=https://archive.org/download/flashplayerarchivedversions2/333/fp_29.0.0.171_archive.zip>https://archive.org/download/flashplayerarchivedversions2/333/fp_29.0.0.171_archive.zip</a><br>
&nbsp;&nbsp;&nbsp;&nbsp;1. extract the zip file from the link above<br>
&nbsp;&nbsp;&nbsp;&nbsp;2. run the correct installer(most likely winpep something)<br>
&nbsp;&nbsp;&nbsp;&nbsp;3. Restart the archive and it should load!<br></details>
&nbsp;&nbsp;&nbsp;&nbsp;<details><summary><ins>Manual 'install' - no admin required</ins><br></summary>
&nbsp;&nbsp;&nbsp;&nbsp;1. download "pepflashplayer.dll" for your system(just search for it on google)<br>
<details>
    &nbsp;&nbsp;&nbsp;&nbsp;<summary>How to verify a .dll from the internet is legit<br></summary>
    &nbsp;&nbsp;&nbsp;&nbsp;1. right click on pepflashplayer.dll(the one extracted from the zip, not the zip itself) and click 'Properties'<br>
    &nbsp;&nbsp;&nbsp;&nbsp;2. click the 'Digital Signatures' tab(if it isn't there don't trust the file)<br>
    &nbsp;&nbsp;&nbsp;&nbsp;3. ensure there is a valid signature from 'Adobe Systens Incorporated'.
   
</details>
&nbsp;&nbsp;&nbsp;&nbsp;2. navigate to %appdata%/Ninja Kiwi Archive/ in file explorer<br>
&nbsp;&nbsp;&nbsp;&nbsp;3. open the folder there named "flash"(create it if it didn't exist)<br>
&nbsp;&nbsp;&nbsp;&nbsp;4. delete anything there previously<br>
&nbsp;&nbsp;&nbsp;&nbsp;5. create a folder called "system"<br>
&nbsp;&nbsp;&nbsp;&nbsp;6. paste the pepflashplayer.dll there<br>
&nbsp;&nbsp;&nbsp;&nbsp;7. restart the archive and it should load!<br>
</details></details>

# Building & self hosting

## MP Components
(BTD5 Co-op, BTD Battles, SAS4, SAS3, Countersnipe)
### With JDK 17+:
0. Add the JDK to your system PATH, or edit the script to an absolute one in the next step.<br>
1. Download the source and run <code>compile.bat</code> or <code>compile.sh</code> depending on your system.

This will compile the source to <code>./classes</code> and launch the server.
To configure the server, edit <code>flash.properties</code>.
You can also create a jar file with <code>mvn package</code> or <code>extra/package.bat</code>, or download one from build artifacts.

### With JDK 8+:
There is an older version of each server which can be compiled here and is a few updates behind, but each server is only a single source file and they have been tested to run consistently for months. However some deadlocks might emerge after thousands of games.

0. Add the JDK to your system PATH, or edit the script to an absolute one in the next step.
1. Navigate to <code>extra/old/[game name]</code> and run <code>runme.bat</code> or <code>runme.sh</code> depending on your system. 
There are no dependencies for either version.

## Web Components
(BTD5 Daily Challenges/Special Missions, BMC, BMC/SAS4 Event Data)

These are a web app. See <a href="https://github.com/GlennnM/FlashPrivateServer/tree/main/bmc#README">the BMC readme</a> for build instructions.<br>

### Client
In order to play on a server you are hosting, you will have to create SWFs that link game clients to your server,<br>
since the ones generated by the powershell scripts only link to my server.<br>
This can be done through decompilation+Fiddler MITM and other methods, but eventually an archive mod with server selection might be available.<br>
Some useful tools for analyzing client and server behavior are <a href=https://github.com/jindrapetrik/jpexs-decompiler>FFDec</a> and <a href=https://www.wireshark.org/download.html>Wireshark</a>. <br>
<br>

# Contact
If you have questions or concerns feel free to message me on discord: glenn_m<br>
or join: <a href=https://discord.gg/VVGuvq7kAv>https://discord.gg/VVGuvq7kAv</a><br><br>

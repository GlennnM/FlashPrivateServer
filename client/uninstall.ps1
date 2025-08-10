$win_steam =${env:ProgramFiles(x86)}+"\Steam\steamapps\common\Ninja Kiwi Archive\resources"
$win_standalone1=${env:LocalAppData}+"\Programs\Ninja Kiwi Archive\resources"
$win_standalone2=${env:ProgramFiles}+"\Ninja Kiwi\Ninja Kiwi Archive\resources"
$mac_steam=$HOME +"/Library/Application Support/Steam/steamapps/common/Ninja Kiwi Archive/resources"
$mac_standalone="/Applications/Ninja Kiwi Archive.app/Contents/Resources"
$linux_steam = $HOME + "/.steam/steam/steamapps/common/Ninja Kiwi Archive"
$linux_steam2 = $HOME + "/.local/share/Steam/steamapps/common/Ninja Kiwi Archive"
$linux_proton = $HOME + "/.steam/steam/steamapps/compatdata/1275350/pfx/drive_c/Program Files (x86)/Steam/steamapps/common/Ninja Kiwi Archive"
function Uninst([string]$cache){
	if(Test-Path -Path $cache'/appv.asar'){
		if(Test-Path -Path $cache'/app.asar'){
			Remove-Item $cache'/app.asar'
		}
	    Rename-Item -Path $cache'/appv.asar' -NewName "app.asar"
	    "Restored original file from "+$cache
	}
	
}
if (Test-Path -Path $cache) {
    "Flash Private Server Installer by glenn m"
    "All mods will be uninstalled."
    "==============================="
    $X= Read-Host "Please ensure all Ninja Kiwi Archive windows(INCLUDING THE LAUNCHER!!!) are closed, then press ENTER to begin uninstallation..."
    $N = (New-Object Net.WebClient)
    "Restoring appv.asar files..."
    Uninst -cache $win_steam
    Uninst -cache $win_standalone1
    Uninst -cache $win_standalone2
    Uninst -cache $mac_steam
    Uninst -cache $mac_standalone
    Uninst -cache $linux_steam
    Uninst -cache $linux_steam2
    Uninst -cache $linux_proton
} else {
    "Ninja Kiwi Archive not found."
    $Q = Read-Host "Press enter to exit..."
    Exit
}

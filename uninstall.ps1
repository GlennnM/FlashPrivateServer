"Checking for Ninja Kiwi Archive..."
if ($IsWindows -or $ENV:OS) {
    $cache = $env:APPDATA+'/Ninja Kiwi Archive/Cache'
} else {
   $cache = $HOME+'/Library/Application Support/Ninja Kiwi Archive/Cache'
}
if (Test-Path -Path $cache) {
    "Flash Private Server Installer by glenn m"
    "All mods will be uninstalled."
    "==============================="
    $X= Read-Host "Please ensure all Ninja Kiwi Archive windows(INCLUDING THE LAUNCHER!!!) are closed, then press ENTER to begin uninstallation..."
    $N = (New-Object Net.WebClient)
    "Clearing archive cache..."
    try{
        Remove-Item $cache'/*' -Recurse
        "Archive cache cleared!"
        $Q = Read-Host "Press enter to exit..."
    }catch{
        $Q = Read-Host "Clearing cache failed, exiting."
    }
       
    
} else {
    "Ninja Kiwi Archive not found."
    $Q = Read-Host "Press enter to exit..."
    Exit
}

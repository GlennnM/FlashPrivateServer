"Checking for Ninja Kiwi Archive..."
$Folder = $env:APPDATA+'/Ninja Kiwi Archive/Cache'
if (Test-Path -Path $Folder) {
    "Flash Private Server Installer by glenn m"
    "All mods will be uninstalled."
    "==============================="
    $X= Read-Host "Please ensure all Ninja Kiwi Archive windows(INCLUDING THE LAUNCHER!!!) are closed, then press ENTER to begin uninstallation..."
    $N = (New-Object Net.WebClient)
    "Clearing archive cache..."
    try{
        Remove-Item $env:APPDATA'/Ninja Kiwi Archive/Cache/*' 
        "Archive cache cleared!"
    }catch{
        $Q = Read-Host "Clearing cache failed, exiting."
    }
       
    
} else {
    "Ninja Kiwi Archive not found."
    $Q = Read-Host "Press enter to exit..."
    Exit
}
"Checking for Ninja Kiwi Archive..."
$Folder = $env:APPDATA+'/Ninja Kiwi Archive/Cache'
if (Test-Path -Path $Folder) {
    "Flash Private Server Installer by glenn m"
    "The following mods will be installed:"
    "Battles Flash Private Server"
    "SAS4 Flash Private Server"
    "Approx data size: 18.5 MB"
    "==============================="
    $X= Read-Host "Please ensure all Ninja Kiwi Archive windows(INCLUDING THE LAUNCHER!!!) are closed, then press ENTER to begin installation..."
    $N = (New-Object Net.WebClient)
    "Clearing archive cache..."
    try{
        Remove-Item $env:APPDATA'/Ninja Kiwi Archive/Cache/*' 
    }catch{
        $Q = Read-Host "Clearing cache failed, exiting."
        Exit
    }
    "Archive cache cleared!"
       try{
        "Downloading data_0..."
        $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/data_0', $env:APPDATA+'/Ninja Kiwi Archive/Cache/data_0')
       "Downloading data_1..."
        $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/data_1', $env:APPDATA+'/Ninja Kiwi Archive/Cache/data_1')
        "Downloading data_2..."
       $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/data_2', $env:APPDATA+'/Ninja Kiwi Archive/Cache/data_2')
        "Downloading data_3..."
        $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/data_3', $env:APPDATA+'/Ninja Kiwi Archive/Cache/data_3')
        "Downloading f_000001..."
        $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/f_000001', $env:APPDATA+'/Ninja Kiwi Archive/Cache/f_000001')
        "Downloading f_000002..."
        $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/f_000002', $env:APPDATA+'/Ninja Kiwi Archive/Cache/f_000002')
        "Downloading f_000003..."
        $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/f_000003', $env:APPDATA+'/Ninja Kiwi Archive/Cache/f_000003')
        "Downloading f_000004..."
        $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/f_000004', $env:APPDATA+'/Ninja Kiwi Archive/Cache/f_000004')
        "Downloading index..."
        $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/index', $env:APPDATA+'/Ninja Kiwi Archive/Cache/index')
        "Mod installation successful!! You can play on private servers through Ninja Kiwi Archive."
        $X=Read-Host "Press enter to exit..."
          }catch{
             $Q = Read-Host "One or more files failed to download, exiting."
    }
} else {
    "Ninja Kiwi Archive not found. Please install it from ninjakiwi.com or on Steam, and play a few games to generate the cache folder."
    $Q = Read-Host "Press enter to exit..."
    Exit
}
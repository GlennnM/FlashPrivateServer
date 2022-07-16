"Checking for Ninja Kiwi Archive..."
if ($IsWindows -or $ENV:OS) {
    $cache = $env:APPDATA+'/Ninja Kiwi Archive/Cache'
} else {
   $cache = $HOME+'/Library/Application Support/Ninja Kiwi Archive/Cache'
}

if (Test-Path -Path $cache) {
    "Flash Private Server Installer by glenn m"
    "The following mods will be installed:"
    "Battles Flash Private Server"
    "SAS4 Flash Private Server"
    "BTD5 Flash Co-op/Challenges/Missions Private Server"
    "Approx data size: 37.3 MB"
    "*******************************"
    "IMPORTANT!!!!!! You are installing the localhost version. This is for testing/hosting your own servers."
    "If this is a mistake and you just want to play the game, use FlashClient.ps1 instead."
    "==============================="

    $X= Read-Host "Please ensure all Ninja Kiwi Archive windows(INCLUDING THE LAUNCHER!!!) are closed, then press ENTER to begin installation..."
    $N = (New-Object Net.WebClient)
    "Clearing archive cache..."
    try{
        Remove-Item $cache'/*' -Recurse
    }catch{
        $Q = Read-Host "Clearing cache failed, exiting."
        Exit
    }
    "Archive cache cleared!"
       try{
           if ($IsWindows -or $ENV:OS) {
                "Downloading data_0...(1/10)"
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/Installer_localhost/data_0', $cache+'/data_0')
               "Downloading data_1...(2/10)"
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/Installer_localhost/data_1', $cache+'/data_1')
                "Downloading data_2...(3/10)"
               $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/Installer_localhost/data_2', $cache+'/data_2')
                "Downloading data_3...(4/10)"
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/Installer_localhost/data_3', $cache+'/data_3')
                "Downloading f_000001...(5/10)"
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/Installer_localhost/f_000001', $cache+'/f_000001')
                "Downloading f_000002...(6/10)"
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/Installer_localhost/f_000002', $cache+'/f_000002')
                "Downloading f_000003...(7/10)"
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/Installer_localhost/f_000003', $cache+'/f_000003')
                "Downloading f_000004...(8/10)"
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/Installer_localhost/f_000004', $cache+'/f_000004')
                "Downloading f_000005...(9/10)"
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/Installer_localhost/f_000005', $cache+'/f_000005')
                "Downloading index...(10/10)"
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/Installer_localhost/index', $cache+'/index')
                
            }else{
                "No localhost swf cache files exist for your os, i was too lazy"
            }"Mod installation successful!!!! You can play on self hosted servers through Ninja Kiwi Archive."
			"Note: Cache files are temporary; if it stops working after a few days simply run this script again."
                $X=Read-Host "Press enter to exit..."
          }catch{
             $Q = Read-Host "One or more files failed to download, exiting."
    }
} else {
    "Ninja Kiwi Archive not found. Please install it from ninjakiwi.com or on Steam, and play a few games to generate the cache folder."
    $Q = Read-Host "Press enter to exit..."
    Exit
}
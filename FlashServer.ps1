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
    "Approx data size: 18.5 MB"
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
                "Downloading data_0..."
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/data_0', $cache+'/data_0')
               "Downloading data_1..."
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/data_1', $cache+'/data_1')
                "Downloading data_2..."
               $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/data_2', $cache+'/data_2')
                "Downloading data_3..."
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/data_3', $cache+'/data_3')
                "Downloading f_000001..."
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/f_000001', $cache+'/f_000001')
                "Downloading f_000002..."
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/f_000002', $cache+'/f_000002')
                "Downloading f_000003..."
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/f_000003', $cache+'/f_000003')
                "Downloading f_000004..."
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/f_000004', $cache+'/f_000004')
                "Downloading index..."
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/index', $cache+'/index')
                
            }else{
                "Downloading 27d32bfbe2be4e0e_0..."
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer_v3cache/27d32bfbe2be4e0e_0', $cache+'/27d32bfbe2be4e0e_0')
                "Downloading 4fd66772242a0ed2_0..."
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer_v3cache/4fd66772242a0ed2_0', $cache+'/4fd66772242a0ed2_0')
                "Downloading 47aa2e2b3ad33299_0..."
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer_v3cache/47aa2e2b3ad33299_0', $cache+'/47aa2e2b3ad33299_0')
                "Downloading cc91a1312883f7af_0..."
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer_v3cache/cc91a1312883f7af_0', $cache+'/cc91a1312883f7af_0')
                "Downloading index..."
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer_v3cache/index', $cache+'/index')
                "Creating index_dir..."
                New-Item -Path $cache'/' -ItemType "directory" -Name "index-dir"
                "Downloading index-dir/the-real-index..."
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer_v3cache/index-dir/the-real-index', $cache+'/index-dir/the-real-index')
                
            }"Mod installation successful!! You can play on private servers through Ninja Kiwi Archive."
                $X=Read-Host "Press enter to exit..."
          }catch{
             $Q = Read-Host "One or more files failed to download, exiting."
    }
} else {
    "Ninja Kiwi Archive not found. Please install it from ninjakiwi.com or on Steam, and play a few games to generate the cache folder."
    $Q = Read-Host "Press enter to exit..."
    Exit
}
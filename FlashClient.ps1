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
    "SAS3 Flash Private Server"
    "Countersnipe Private Server"
    "Approx data size: 44.5 MB"
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
    "Checking version..."
    $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/is_latest_v3.0.txt', $cache+'/test.txt')
    Remove-Item $cache'/test.txt'
    "Version check successful!"
    }catch{
        "ERROR: script is not up to date, or you don't have an internet connection. Please update it at https://github.com/GlennnM/NKFlashServers"
        $Q = Read-Host "Press enter to exit..."
        Exit
    }
       try{
           if ($IsWindows -or $ENV:OS) {
               
                "Downloading data_0...(1/11)"
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/data_0', $cache+'/data_0')
               "Downloading data_1...(2/11)"
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/data_1', $cache+'/data_1')
                "Downloading data_2...(3/11)"
               $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/data_2', $cache+'/data_2')
                "Downloading data_3...(4/11)"
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/data_3', $cache+'/data_3')
                "Downloading f_000001...(5/11)"
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/f_000001', $cache+'/f_000001')
                "Downloading f_000002...(6/11)"
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/f_000002', $cache+'/f_000002')
                "Downloading f_000003...(7/11)"
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/f_000003', $cache+'/f_000003')
                "Downloading f_000004...(8/11)"
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/f_000004', $cache+'/f_000004')
                "Downloading f_000005...(9/11)"
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/f_000005', $cache+'/f_000005')
                "Downloading f_000006...(10/11)"
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/f_000006', $cache+'/f_000006')
                "Downloading index...(11/11)"
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/index', $cache+'/index')
                
            }else{
                "Downloading 4fd66772242a0ed2_0...(1/7)"
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/osx/4fd66772242a0ed2_0', $cache+'/4fd66772242a0ed2_0')
                "Downloading 5fa3c63f0c5a62a3_0...(2/7)"
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/osx/5fa3c63f0c5a62a3_0', $cache+'/5fa3c63f0c5a62a3_0')
                "Downloading 23d78fbb3d9b1dca_0...(3/7)"
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/osx/23d78fbb3d9b1dca_0', $cache+'/23d78fbb3d9b1dca_0')
                "Downloading 16247a1cfd794ab2_0...(4/7)"
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/osx/16247a1cfd794ab2_0', $cache+'/16247a1cfd794ab2_0')
				"Downloading cc91a1312883f7af_0...(5/7)"
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/osx/cc91a1312883f7af_0', $cache+'/cc91a1312883f7af_0')
                "Downloading index...(6/7)"
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/osx/index', $cache+'/index')
                "Creating index_dir..."
                New-Item -Path $cache'/' -ItemType "directory" -Name "index-dir"
                "Downloading index-dir/the-real-index...(7/7)"
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/osx/index-dir/the-real-index', $cache+'/index-dir/the-real-index')
                
            }"Mod installation successful!!!! You can play on private servers through Ninja Kiwi Archive."
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
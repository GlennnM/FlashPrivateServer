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
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/data_0', $cache+'/data_0')
               "Downloading data_1...(2/10)"
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/data_1', $cache+'/data_1')
                "Downloading data_2...(3/10)"
               $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/data_2', $cache+'/data_2')
                "Downloading data_3...(4/10)"
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/data_3', $cache+'/data_3')
                "Downloading f_000001...(5/10)"
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/f_000001', $cache+'/f_000001')
                "Downloading f_000002...(6/10)"
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/f_000002', $cache+'/f_000002')
                "Downloading f_000003...(7/10)"
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/f_000003', $cache+'/f_000003')
                "Downloading f_000004...(8/10)"
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/f_000004', $cache+'/f_000004')
                "Downloading f_000005...(9/10)"
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/f_000005', $cache+'/f_000005')
                "Downloading index...(10/10)"
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer/index', $cache+'/index')
                
            }else{
                "Downloading 23d78fbb3d9b1dca_0...(1/14)"
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer_osx/23d78fbb3d9b1dca_0', $cache+'/23d78fbb3d9b1dca_0')
                "Downloading 248e3ebb471e380f_0...(2/14)"
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer_osx/248e3ebb471e380f_0', $cache+'/248e3ebb471e380f_0')
                "Downloading 27d32bfbe2be4e0e_0...(3/14)"
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer_osx/27d32bfbe2be4e0e_0', $cache+'/27d32bfbe2be4e0e_0')
                "Downloading 2a0a161809266731_0...(4/14)"
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer_osx/2a0a161809266731_0', $cache+'/2a0a161809266731_0')
				"Downloading 3148b756f68199ba_0...(5/14)"
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer_osx/3148b756f68199ba_0', $cache+'/3148b756f68199ba_0')
                "Downloading 47aa2e2b3ad33299_0...(6/14)"
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer_osx/47aa2e2b3ad33299_0', $cache+'/47aa2e2b3ad33299_0')
                "Downloading 4fd66772242a0ed2_0...(7/14)"
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer_osx/4fd66772242a0ed2_0', $cache+'/4fd66772242a0ed2_0')
                "Downloading 6a6ac862231294c7_0...(8/14)"
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer_osx/6a6ac862231294c7_0', $cache+'/6a6ac862231294c7_0')
				"Downloading 7be99867e1cceb85_0...(9/14)"
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer_osx/7be99867e1cceb85_0', $cache+'/7be99867e1cceb85_0')
                "Downloading cc91a1312883f7af_0...(10/14)"
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer_osx/cc91a1312883f7af_0', $cache+'/cc91a1312883f7af_0')
                "Downloading e0ab11c96f70dcb2_0...(11/14)"
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer_osx/e0ab11c96f70dcb2_0', $cache+'/e0ab11c96f70dcb2_0')
                "Downloading f118ce0562c80012_0...(12/14)"
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer_osx/f118ce0562c80012_0', $cache+'/f118ce0562c80012_0')
				"Downloading ff9b5fa4d1d85f16_0...(13/14)"
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer_osx/ff9b5fa4d1d85f16_0', $cache+'/ff9b5fa4d1d85f16_0')
                "Downloading index..."
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer_osx/index', $cache+'/index')
                "Creating index_dir..."
                New-Item -Path $cache'/' -ItemType "directory" -Name "index-dir"
                "Downloading index-dir/the-real-index...(14/14)"
                $N.DownloadFile('https://github.com/GlennnM/NKFlashServers/raw/main/Installer_osx/index-dir/the-real-index', $cache+'/index-dir/the-real-index')
                
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
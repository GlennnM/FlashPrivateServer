"Checking for Ninja Kiwi Archive..."
if ($IsWindows -or $ENV:OS) {
	$cache = $env:APPDATA + '\Ninja Kiwi Archive\Cache'
	$zippath = 'https://github.com/GlennnM/FlashPrivateServer/releases/download/v3.4/cache_windows.zip'
	$filename = 'cache_windows.zip'
	$FULL_SIZE = 44621892
} else {
	$cache = $HOME + '/Library/Application Support/Ninja Kiwi Archive/Cache'
	$zippath = 'https://github.com/GlennnM/FlashPrivateServer/releases/download/v3.4/cache_osx.zip'
	$filename = 'cache_osx.zip'
	$FULL_SIZE = 44595449
}
[int]$FULL_MB = $FULL_SIZE / 0.1MB
$FULL_MB_FLOAT = $FULL_MB / 10
$downloadpath = $cache + "/install.zip"
if (Test-Path -Path $cache) {
	"Flash Private Server Installer by glenn m"
	"The following mods will be installed:"
	"Battles Flash Private Server"
	"SAS4 Flash Private Server"
	"BTD5 Flash Co-op/Challenges/Missions Private Server"
	"SAS3 Flash Private Server"
	"Countersnipe Private Server"
	"Approx data size: $FULL_MB_FLOAT MB"
	"==============================="

	$X = Read-Host "Please ensure all Ninja Kiwi Archive windows(INCLUDING THE LAUNCHER!!!) are closed, then press ENTER to begin installation..."
	$N = (New-Object Net.WebClient)
	"Clearing archive cache..."
	try {
		Remove-Item $cache'/*' -Recurse
	} catch {
		$Q = Read-Host "Clearing cache failed, exiting."
		exit
	}
	"Archive cache cleared!"
	try {
		"Checking version..."
		$N.DownloadFile('https://github.com/GlennnM/FlashPrivateServer/raw/main/v3.4.txt',$cache + '/test.txt')
		Remove-Item $cache'/test.txt'
		"Version check successful!"
	} catch {
		"ERROR: script is not up to date, or you don't have an internet connection. Please update it at https://github.com/GlennnM/NKFlashServers"
		$Q = Read-Host "Press enter to exit..."
		exit
	}
	try {
		Set-Location $cache
		Write-Host -NoNewline "Downloading $filename ... "
		$E = $N.DownloadFileTaskAsync($zippath,$downloadpath)
		while (!($E.IsCompleted)) {
			Start-Sleep -Seconds 1
			try {
				$size = (Get-Item -Path $downloadpath).Length
				if ($size -eq 0) {
					continue
				}
				[int]$percent = ($size / $FULL_SIZE * 1000)
				[int]$mb = $size / 0.1MB
				$percent_float = $percent / 10
				$mb_float = $mb / 10
				#Write-Progress -Activity "Downloading cache_windows.zip:" -Status "$percent_float% complete.." -PercentComplete $percent_float
				Write-Host -NoNewline "`rDownloading $filename ... $percent_float% ($mb_float MB/$FULL_MB_FLOAT MB)    "
			} catch {

			}
		}
		if ($size -lt $FULL_SIZE) {
			"`n"
			throw
		}
		Write-Host -NoNewline "`rDownloading $filename complete!                        "
		#Invoke-RestMethod -ContentType "application/octet-stream" $zippath -OutFile $cache'/install.zip'
		#Start-BitsTransfer -Source "$zippath" -Destination "$downloadpath"
		"`nExtracting..."
		try {
			"Attempting unzip method 1(powershell 5+)..."
			Expand-Archive -Path "$cache/install.zip" -DestinationPath "$cache/"

		} catch {
			try {
				"Attempting unzip method 2(.NET)..."
				Add-Type -AssemblyName System.IO.Compression.FileSystem
				function Unzip
				{
					param([string]$zipfile,[string]$outpath)

					[System.IO.Compression.ZipFile]::ExtractToDirectory($zipfile,$outpath)
				}

				Unzip "$cache/install.zip" "$cache/"
			} catch {
				try {
					"Attempting unzip method 3(tar -xvf)"
					Start-Process tar -Wait -NoNewWindow -ArgumentList -WorkingDirectory "$cache" @("-xvf","install.zip")

				} catch {
					if ($IsWindows -or $ENV:OS) {
						"Attempting unzip method 4(7zip)"
						Start-Process -Wait -FilePath "C:\Program Files\7-Zip\7z.exe" -NoNewWindow -WorkingDirectory "$cache" -ArgumentList @("e","install.zip")
					} else {
						"Attempting unzip method 4(unzip)"
						Start-Process Unzip -Wait -NoNewWindow -WorkingDirectory "$cache" -ArgumentList @("install.zip")
					}
				}

			}
		}
		Remove-Item "$cache/install.zip"

		if (Test-Path -Path $cache'/index') {

			"Mod installation successful!!!! You can play on private servers through Ninja Kiwi Archive."
			"Note: Cache files are temporary; if it stops working after a few days simply run this script again."
		} else {
			"Mod installation failed."
		}
		$X = Read-Host "Press enter to exit..."
	} catch {
		$Q = Read-Host "One or more files failed to download, exiting."
	} finally {
		try {
			$N.CancelAsync();
		} catch {

		}
		$N.Dispose();
	}
	#Write-Progress -Activity "Downloading cache_windows.zip:" -Completed
} else {
	"Ninja Kiwi Archive not found. Please install it from ninjakiwi.com or on Steam, and open it to generate the cache folder."
	$Q = Read-Host "Press enter to exit..."
	exit
}

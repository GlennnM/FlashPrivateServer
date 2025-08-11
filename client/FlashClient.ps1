
$VERSION = "4.0"
$SIZE = 118382691
$win_steam =${env:ProgramFiles(x86)}+"\Steam\steamapps\common\Ninja Kiwi Archive\resources"
$win_standalone1=${env:LocalAppData}+"\Programs\Ninja Kiwi Archive\resources"
$win_standalone2=${env:ProgramFiles}+"\Ninja Kiwi\Ninja Kiwi Archive\resources"
$mac_steam=$HOME +"/Library/Application Support/Steam/steamapps/common/Ninja Kiwi Archive/resources"
$mac_standalone="/Applications/Ninja Kiwi Archive.app/Contents/Resources"
$linux_steam = $HOME + "/.steam/steam/steamapps/common/Ninja Kiwi Archive"
$linux_steam2 = $HOME + "/.local/share/Steam/steamapps/common/Ninja Kiwi Archive"
$linux_proton = $HOME + "/.steam/steam/steamapps/compatdata/1275350/pfx/drive_c/Program Files (x86)/Steam/steamapps/common/Ninja Kiwi Archive"
$URL="https://github.com/GlennnM/FlashPrivateServer/releases/download/v" + $VERSION +"/app.zip"
$filename="app.asar"
$global:count_=0
[int]$MB = $SIZE / 0.1MB
$MB_FLOAT = $MB / 10
function DownloadThenExtract([string]$cache,[string]$zippath,[string]$downloadpath,[int]$FULL_SIZE){
	[int]$FULL_MB = $FULL_SIZE / 0.1MB
	$FULL_MB_FLOAT = $FULL_MB / 10
	$N = (New-Object Net.WebClient)
	try {
		Write-Host -NoNewline "Downloading $filename ... "
		$E = $N.DownloadFileTaskAsync($zippath,$downloadpath)
		while (!($E.IsCompleted)) {
			Start-Sleep -Seconds 1
                if(Test-Path -Path $downloadpath){
				    $size = (Get-Item -Path $downloadpath).Length
				    if ($size -eq 0) {
					    continue
				    }
				    [int]$percent = ($size / $FULL_SIZE * 1000)
				    [int]$mb = $size / 0.1MB
				    $percent_float = $percent / 10
				    $mb_float = $mb / 10
				    #Write-Progress -Activity "Downloading app.asar:" -Status "$percent_float% complete.." -PercentComplete $percent_float
				    Write-Host -NoNewline "`rDownloading $filename ... $percent_float% ($mb_float MB/$FULL_MB_FLOAT MB)    "
                
                }
			
		}
		if ($size -ne $FULL_SIZE) {
			"`n"
			throw
		}
		if(Test-Path -Path $cache'/install.zip'){
		}else{
			"`nDownload failed. You might need to run as an admin, or check your internet connection."
			return
		}
		Write-Host -NoNewline "`rDownloading $filename complete!                        "
        if(Test-Path -Path $cache'/app.asar'){
        	if(Test-Path -Path $cache'/appv.asar'){
                Remove-Item -Path $cache'/appv.asar'
			}
            try{
		    	Rename-Item -Path $cache'/app.asar' -NewName "appv.asar"
            }catch{
                "Archive was probably not closed, or you don't have permissions. Installation failed."
                return
            }
        }
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

		if (Test-Path -Path $cache'/app.asar') {
            $global:count_++

			"Mod installation for NK Archive at "+$cache+" probably succeeded!!"
		} else {
			"Mod installation failed."
		}
	} catch {
		"One or more files failed to download"
	} finally {
		try {
			$N.CancelAsync();
		} catch {

		}
		$N.Dispose();
	}
}
"Flash Private Server Installer by glenn m"
	"The following mods will be installed:"
	"Flash Private Server"
    "BTD5, Battles, BMC, SAS3, SAS4, Countersnipe"
    "Enable hidden+extra archive games"
	"Approx data size: $MB_FLOAT MB"
	"==============================="
"Checking version..."
$N = (New-Object Net.WebClient)
try{
	$N.DownloadFile('https://github.com/GlennnM/FlashPrivateServer/raw/main/version.txt','./test.txt')
	$V = (Get-Content './test.txt' -Raw)
	Remove-Item './test.txt'
	if ($null -eq $V){
		"ERROR: script is not up to date, or you don't have an internet connection. Please update it at https://github.com/GlennnM/FlashPrivateServer"
		$Q = Read-Host "Press enter to exit..."
		exit
	}
	if ($V -ne $VERSION) {
		"ERROR: script is not up to date. Please update it at https://github.com/GlennnM/FlashPrivateServer"
		$Q = Read-Host "Press enter to exit..."
		exit
	}
}catch{
	"ERROR: script is not up to date, or you don't have an internet connection. Please update it at https://github.com/GlennnM/FlashPrivateServer"
	$Q = Read-Host "Press enter to exit..."
	exit
}
finally{
	try {
		$N.CancelAsync();
	} catch {

	}
	$N.Dispose();
}
    "If you have a non-Steam installation, make sure to run this as an admin!!"
	$X = Read-Host "Please ensure all Ninja Kiwi Archive windows(INCLUDING THE LAUNCHER!!!) are closed, then press ENTER to begin installation..."

if ($IsWindows -or $ENV:OS) {
    if (Test-Path -Path $win_steam) {
        "Located windows/steam installation(probably)"
        DownloadThenExtract -cache $win_steam -zippath $URL -downloadpath $win_steam'/install.zip' -FULL_SIZE $SIZE
    }
    if (Test-Path -Path $win_standalone1) {
        "Located windows/standalone installation(probably)"
        DownloadThenExtract -cache $win_standalone1 -zippath $URL -downloadpath $win_standalone1'/install.zip' -FULL_SIZE $SIZE
    }
    if (Test-Path -Path $win_standalone2) {
        "Located windows/standalone(All Users) installation(probably)"
        "If this fails, try running powershell as admin"
        "(or, if one of the previous steps succeeded, just use that archive instead)"
        try{
        DownloadThenExtract -cache $win_standalone2 -zippath $URL -downloadpath $win_standalone2'/install.zip' -FULL_SIZE $SIZE
        }catch{

        }
    }
}else{
    if(Test-Path -Path $mac_steam){
        "Located mac/steam installation(probably)"
        
        DownloadThenExtract -cache $mac_steam -zippath $URL -downloadpath $mac_steam'/install.zip' -FULL_SIZE $SIZE

    }if(Test-Path -Path $mac_standalone){
        "Located mac/standalone installation(probably)"
        DownloadThenExtract -cache $mac_standalone -zippath $URL -downloadpath $mac_standalone'/install.zip' -FULL_SIZE $SIZE

    }
    if(Test-Path -Path $linux_steam){
        "Located linux/steam installation(probably)"
        
        DownloadThenExtract -cache $linux_steam -zippath $URL -downloadpath $linux_steam'/install.zip' -FULL_SIZE $SIZE

    }if(Test-Path -Path $linux_steam2){
        "Located linux/steam installation(probably)"
        DownloadThenExtract -cache $linux_steam2 -zippath $URL -downloadpath $linux_steam2'/install.zip' -FULL_SIZE $SIZE

    }if(Test-Path -Path $linux_proton){
        "Located linux/proton installation(probably)"
        DownloadThenExtract -cache $linux_proton -zippath $URL -downloadpath $linux_proton'/install.zip' -FULL_SIZE $SIZE

    }
}
"Successful installations: "+$global:count_
if($global:count_ -gt 0){
    "You can now play multiplayer on the NK Archive! If some installations failed, scroll up to see which ones succeeded."
}
$X = Read-Host "Press enter to exit..."
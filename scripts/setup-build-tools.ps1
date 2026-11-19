$b = "$env:LOCALAPPDATA\TiviTuCapNhat\build"
[Net.ServicePointManager]::SecurityProtocol = 'Tls12'
$ProgressPreference = 'SilentlyContinue'
New-Item -ItemType Directory -Force "$b\dl", "$b\sdk\cmdline-tools" | Out-Null

if (-not (Test-Path "$b\jdk\bin\java.exe")) {
  $j = Invoke-RestMethod 'https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse' -Headers @{ 'User-Agent' = 'tv-setup' }
  $p = $j[0].binary.package
  Invoke-WebRequest $p.link -OutFile "$b\dl\jdk.zip" -UseBasicParsing
  if ((Get-FileHash "$b\dl\jdk.zip" -Algorithm SHA256).Hash.ToLower() -ne $p.checksum) { throw 'jdk checksum mismatch' }
  tar -xf "$b\dl\jdk.zip" -C "$b\dl"
  Move-Item (Get-ChildItem "$b\dl" -Directory | Where-Object Name -like 'jdk-*' | Select-Object -First 1).FullName "$b\jdk"
  "jdk ok"
}
if (-not (Test-Path "$b\sdk\cmdline-tools\latest\bin\sdkmanager.bat")) {
  Invoke-WebRequest 'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip' -OutFile "$b\dl\cmd.zip" -UseBasicParsing
  New-Item -ItemType Directory -Force "$b\dl\cmd" | Out-Null
  tar -xf "$b\dl\cmd.zip" -C "$b\dl\cmd"
  Move-Item "$b\dl\cmd\cmdline-tools" "$b\sdk\cmdline-tools\latest"
  "cmdline ok"
}
$env:JAVA_HOME = "$b\jdk"
$env:Path = "$b\jdk\bin;$env:Path"
(1..40 | ForEach-Object { 'y' }) -join "`n" | Set-Content "$b\yes.txt" -Encoding Ascii
cmd /c "`"$b\sdk\cmdline-tools\latest\bin\sdkmanager.bat`" --sdk_root=`"$b\sdk`" --licenses < `"$b\yes.txt`" > `"$b\lic.log`" 2>&1"
cmd /c "`"$b\sdk\cmdline-tools\latest\bin\sdkmanager.bat`" --sdk_root=`"$b\sdk`" `"platforms;android-34`" `"build-tools;34.0.0`" < `"$b\yes.txt`" > `"$b\sdk.log`" 2>&1"
Get-ChildItem "$b\sdk\platforms", "$b\sdk\build-tools" -ErrorAction SilentlyContinue | ForEach-Object Name
"SETUP_DONE"

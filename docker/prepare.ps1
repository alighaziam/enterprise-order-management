$ErrorActionPreference = 'Stop'
$repo = Resolve-Path (Join-Path $PSScriptRoot '..')
$runtime = Join-Path $repo 'docker\runtime'
New-Item -ItemType Directory -Force -Path $runtime | Out-Null

$java = Join-Path $runtime 'java\bin\java'
if (-not (Test-Path $java)) {
    $archive = Join-Path $runtime 'jre.tar.gz'
    $url = 'https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.5%2B8/OpenJDK25U-jre_x64_linux_hotspot_25.0.5_8.tar.gz'
    Write-Output "Downloading Temurin 25 JRE"
    curl.exe -fL --retry 3 -o $archive $url
    $extract = Join-Path $runtime 'java'
    if (Test-Path $extract) { Remove-Item -Recurse -Force $extract }
    New-Item -ItemType Directory -Force -Path $extract | Out-Null
    tar -xzf $archive -C $extract --strip-components=1
}

$mvnCommand = (Get-Command mvn -ErrorAction SilentlyContinue).Source
if (-not $mvnCommand) {
    $candidate = Join-Path $env:USERPROFILE 'tools\apache-maven-3.9.16\bin\mvn.cmd'
    if (-not (Test-Path $candidate)) {
        throw 'Maven 3.9.16+ was not found. Add mvn to PATH or install it under %USERPROFILE%\tools\apache-maven-3.9.16.'
    }
    $mvnCommand = $candidate
}
$settings = Join-Path $PSScriptRoot 'maven-settings.xml'
& $mvnCommand -s $settings -f (Join-Path $repo 'pom.xml') -B -DskipTests package "-Daether.connector.connectTimeout=180000" "-Daether.connector.requestTimeout=180000" "-Djava.net.preferIPv4Stack=true"
Copy-Item -Force (Join-Path $repo 'target\enterprise-order-management-0.1.0-SNAPSHOT.jar') (Join-Path $runtime 'app.jar')
Write-Output "Runtime is ready"

param(
    [string]$UpstreamArchive
)

$ErrorActionPreference = 'Stop'
$revision = 'ecd5ab2980de79eb955621cef26a3b4672013875'
$project = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$download = $null

Add-Type -AssemblyName System.IO.Compression.FileSystem
try {
    if ($UpstreamArchive) {
        $archivePath = (Resolve-Path -LiteralPath $UpstreamArchive).Path
    } else {
        $download = Join-Path ([IO.Path]::GetTempPath()) ('windroid-' + [Guid]::NewGuid() + '.zip')
        $url = "https://codeload.github.com/brunodev85/winlator-app/zip/$revision"
        Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $download
        $archivePath = $download
    }

    $prefix = "winlator-app-$revision/"
    $runtimeEntry = '^app/src/main/(assets/(?:[\w.-]+/)*[\w.-]+\.(?:tzst|sf2)|jniLibs/arm64-v8a/[\w.-]+\.so)$'
    $archive = [IO.Compression.ZipFile]::OpenRead($archivePath)
    try {
        if ($null -eq $archive.GetEntry("${prefix}app/src/main/assets/rootfs.tzst")) {
            throw "The archive does not contain the Winlator runtime at revision $revision."
        }
        foreach ($entry in $archive.Entries) {
            if (!$entry.FullName.StartsWith($prefix, [StringComparison]::Ordinal)) { continue }
            $relative = $entry.FullName.Substring($prefix.Length)
            if ($relative -cnotmatch $runtimeEntry -or $relative -match '(^|/)\.\.?/') { continue }
            $destination = Join-Path $project $relative
            if (Test-Path -LiteralPath $destination -PathType Leaf) { continue }

            New-Item -ItemType Directory -Force -Path (Split-Path -Parent $destination) | Out-Null
            $pending = "$destination.$([Guid]::NewGuid()).part"
            try {
                [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $pending)
                [IO.File]::Move($pending, $destination)
            } finally {
                if (Test-Path -LiteralPath $pending) { Remove-Item -LiteralPath $pending }
            }
        }
    } finally {
        $archive.Dispose()
    }
} finally {
    if ($download -and (Test-Path -LiteralPath $download)) { Remove-Item -LiteralPath $download }
}

Write-Host 'Winlator runtime assets are ready.'

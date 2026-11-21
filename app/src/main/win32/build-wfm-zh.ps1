param(
    [Parameter(Mandatory = $true)][string]$SourceDirectory,
    [Parameter(Mandatory = $true)][string]$CompilerDirectory,
    [string]$OutputPath = (Join-Path $PSScriptRoot '../assets/windroid/wfm-zh.exe')
)

$ErrorActionPreference = 'Stop'
$revision = '6b1860895885a6d65c37815308260293341acb5f'
$source = (Resolve-Path -LiteralPath $SourceDirectory).Path
$tools = (Resolve-Path -LiteralPath $CompilerDirectory).Path
$output = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputPath)
$tempRoot = (Resolve-Path -LiteralPath ([System.IO.Path]::GetTempPath())).Path.TrimEnd('\')
$stage = (New-Item -ItemType Directory -Path (Join-Path $tempRoot ("windroid-wfm-" + [Guid]::NewGuid()))).FullName
try {
    $archive = Join-Path $stage 'source.tar'
    & git -C $source archive --format=tar -o $archive $revision
    if ($LASTEXITCODE -ne 0) { throw "Upstream source revision $revision is unavailable." }
    & tar -xf $archive -C $stage
    if ($LASTEXITCODE -ne 0) { throw 'Could not unpack Winlator File Manager sources.' }
    & git -C $stage apply --ignore-space-change (Join-Path $PSScriptRoot 'wfm-zh.patch')
    if ($LASTEXITCODE -ne 0) { throw 'Could not apply the Simplified Chinese translation.' }

    Push-Location -LiteralPath $stage
    try {
        & (Join-Path $tools 'x86_64-w64-mingw32-windres.exe') '-Iinclude' '-Iinclude/libcdio' '-Ires' '-i' 'res/resource.rc' '-o' 'wfm-resource.o'
        if ($LASTEXITCODE -ne 0) { throw 'Winlator File Manager resources could not be compiled.' }
        $sources = (Get-ChildItem -LiteralPath src -Filter '*.c' | Sort-Object Name).FullName
        $compilerArgs = @(
            '-fno-ident', '-finput-charset=UTF-8', '-O2', '-std=c99', '-DUNICODE', '-D_UNICODE',
            '-DCOBJMACROS', '-DWINVER=0x0600', '-Iinclude', '-Iinclude/libcdio',
            '-Wl,--no-insert-timestamp', '-mwindows', '-s'
        ) + $sources + @(
            'wfm-resource.o', './libcdio.dll', '-lcomctl32', '-lgdi32', '-lole32', '-luuid',
            '-lshell32', '-luser32', '-ladvapi32', '-o', $output
        )
        & (Join-Path $tools 'x86_64-w64-mingw32-clang.exe') @compilerArgs
        if ($LASTEXITCODE -ne 0) { throw 'The localized Winlator File Manager could not be linked.' }
    }
    finally { Pop-Location }
}
finally {
    $resolvedStage = (Resolve-Path -LiteralPath $stage).Path
    if ([System.IO.Path]::GetDirectoryName($resolvedStage) -eq $tempRoot) {
        Remove-Item -LiteralPath $resolvedStage -Recurse -Force
    }
}

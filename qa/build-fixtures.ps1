param(
    [Parameter(Mandatory = $true)][string]$CompilerDirectory,
    [string]$OutputDirectory = (Join-Path $PSScriptRoot '..\dist\qa'),
    [switch]$IncludeRuntimeChecks
)

$ErrorActionPreference = 'Stop'
$compiler = (Resolve-Path -LiteralPath $CompilerDirectory).Path
$out = (New-Item -ItemType Directory -Force -Path $OutputDirectory).FullName
$probe = Join-Path $PSScriptRoot 'probe.c'
$installer = Join-Path $PSScriptRoot 'installer.c'

function Invoke-Compiler([string]$Binary, [string[]]$Arguments) {
    & (Join-Path $compiler $Binary) @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Binary failed with code $LASTEXITCODE" }
}

Invoke-Compiler 'i686-w64-mingw32-clang.exe' @('-Os', '-s', '-municode', '-mwindows', $probe, '-o', "$out\probe-x86.exe")
Invoke-Compiler 'x86_64-w64-mingw32-clang.exe' @('-Os', '-s', '-municode', '-mwindows', $probe, '-o', "$out\probe-x64.exe")

if ($IncludeRuntimeChecks) {
    foreach ($target in @{x86 = 'i686'; x64 = 'x86_64'}.GetEnumerator()) {
        $binary = "$($target.Value)-w64-mingw32-clang.exe"
        Invoke-Compiler $binary @('-Os', '-s', (Join-Path $PSScriptRoot 'gecko-probe.c'), '-lole32', '-loleaut32', '-luuid', '-o', "$out\gecko-$($target.Key).exe")
        Invoke-Compiler $binary @('-Os', '-s', '-municode', (Join-Path $PSScriptRoot 'runtime-probe.c'), '-lversion', '-o', "$out\runtime-$($target.Key).exe")
    }
}

Push-Location -LiteralPath $out
try {
    Invoke-Compiler 'i686-w64-mingw32-windres.exe' @('-i', (Join-Path $PSScriptRoot 'installer.rc'), '-o', 'installer-resource.o')
    Invoke-Compiler 'i686-w64-mingw32-clang.exe' @('-Os', '-s', '-municode', '-mwindows', $installer, 'installer-resource.o', '-lole32', '-luuid', '-lshell32', '-o', 'setup-x86.exe')
    Remove-Item -LiteralPath (Join-Path $out 'installer-resource.o')
}
finally {
    Pop-Location
}

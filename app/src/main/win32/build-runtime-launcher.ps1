param([Parameter(Mandatory = $true)][string]$CompilerDirectory)

$ErrorActionPreference = 'Stop'
$compiler = Join-Path (Resolve-Path -LiteralPath $CompilerDirectory).Path 'x86_64-w64-mingw32-clang.exe'
$source = Join-Path $PSScriptRoot 'runtime-launcher.c'
$destination = Join-Path $PSScriptRoot '../assets/windroid/runtime-launcher.exe'
& $compiler -Os -s -municode -mwindows $source -o $destination
if ($LASTEXITCODE -ne 0) { throw "Runtime launcher build failed with status $LASTEXITCODE" }

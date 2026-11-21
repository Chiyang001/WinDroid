param([Parameter(Mandatory = $true)][string]$CompilerDirectory)

$ErrorActionPreference = 'Stop'
$compiler = Join-Path $CompilerDirectory 'x86_64-w64-mingw32-clang.exe'
$source = Join-Path $PSScriptRoot 'desktop-session.c'
$output = Join-Path $PSScriptRoot '../assets/windroid/desktop-session.exe'
$compilerArgs = @(
    '-Os', '-Wall', '-Wextra', '-Werror', '-ffreestanding', '-nostdlib', '-mwindows', '-s',
    '-Wl,--entry,mainCRTStartup,--no-insert-timestamp', $source, '-luser32', '-lkernel32', '-o', $output
)

& $compiler @compilerArgs
if ($LASTEXITCODE -ne 0) { throw "Desktop session helper compilation failed with code $LASTEXITCODE" }

param(
    [Parameter(Mandatory)][string]$FreeRdpPrefix,
    [Parameter(Mandatory)][string]$NativeBuild,
    [Parameter(Mandatory)][string]$FreeRdpSource,
    [Parameter(Mandatory)][string]$Output,
    [string]$MsysPrefix = 'C:\msys64\ucrt64'
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.ZipFile
$objdump = Join-Path $MsysPrefix 'bin\objdump.exe'
$roots = @((Join-Path $FreeRdpPrefix 'bin'), $NativeBuild,
        (Join-Path $MsysPrefix 'bin'), (Join-Path $MsysPrefix 'lib\ossl-modules'))
$queue = [System.Collections.Queue]::new()
foreach ($name in @('ncatrdp.dll', 'ncatrdploader.dll', 'libfreerdp-client3.dll',
        'libfreerdp3.dll', 'libwinpr3.dll', 'legacy.dll')) {
    $queue.Enqueue($name)
}
$files = [System.Collections.Generic.Dictionary[string,string]]::new([StringComparer]::OrdinalIgnoreCase)
while ($queue.Count -gt 0) {
    $name = [string]$queue.Dequeue()
    if ($files.ContainsKey($name)) { continue }
    $path = $null
    foreach ($root in $roots) {
        $candidate = Join-Path $root $name
        if (Test-Path -LiteralPath $candidate -PathType Leaf) { $path = $candidate; break }
    }
    if (-not $path) { throw "Biblioteca RDP ausente: $name" }
    $files.Add($name, $path)
    foreach ($line in (& $objdump -p $path)) {
        if ($line -notmatch '^\s*DLL Name:\s*(.+)$') { continue }
        $dependency = $Matches[1].Trim()
        foreach ($root in $roots) {
            if (Test-Path -LiteralPath (Join-Path $root $dependency) -PathType Leaf) {
                $queue.Enqueue($dependency)
                break
            }
        }
    }
}

$licenses = @{
    'FreeRDP-LICENSE.txt' = Join-Path $FreeRdpSource 'LICENSE'
    'cJSON-LICENSE.txt' = Join-Path $MsysPrefix 'share\licenses\cjson\LICENSE'
    'libusb-LICENSE.txt' = Join-Path $MsysPrefix 'share\licenses\libusb\COPYING'
    'OpenSSL-LICENSE.txt' = Join-Path $MsysPrefix 'share\licenses\openssl\LICENSE'
    'uriparser-LICENSE.txt' = Join-Path $MsysPrefix 'share\licenses\uriparser\LICENSE'
    'zlib-LICENSE.txt' = Join-Path $MsysPrefix 'share\licenses\zlib\LICENSE'
}
foreach ($path in $licenses.Values) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Licença ausente: $path" }
}

$outputPath = [IO.Path]::GetFullPath($Output)
[IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($outputPath)) | Out-Null
$temporary = "$outputPath.$([Guid]::NewGuid().ToString('N')).tmp"
try {
    $archive = [IO.Compression.ZipFile]::Open($temporary, [IO.Compression.ZipArchiveMode]::Create)
    try {
        foreach ($entry in $files.GetEnumerator()) {
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $entry.Value,
                    $entry.Key, [IO.Compression.CompressionLevel]::Optimal) | Out-Null
        }
        foreach ($entry in $licenses.GetEnumerator()) {
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $entry.Value,
                    $entry.Key, [IO.Compression.CompressionLevel]::Optimal) | Out-Null
        }
    } finally {
        $archive.Dispose()
    }
    Move-Item -LiteralPath $temporary -Destination $outputPath -Force
} finally {
    if (Test-Path -LiteralPath $temporary -PathType Leaf) { Remove-Item -LiteralPath $temporary -Force }
}

$size = (Get-Item -LiteralPath $outputPath).Length
Write-Output "Pacote RDP Windows: $($files.Count) bibliotecas, $size bytes"

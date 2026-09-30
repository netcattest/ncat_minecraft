param(
    [string]$BuildDirectory = "build/remote-native/windows-x64",
    [string]$MsYsBin = "C:/msys64/ucrt64/bin"
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
$build = (Resolve-Path (Join-Path $projectRoot $BuildDirectory)).Path
$msys = (Resolve-Path $MsYsBin).Path
$objdump = Join-Path $msys 'objdump.exe'
$main = Join-Path $build 'ncatrdp.dll'
$loader = Join-Path $build 'ncatrdploader.dll'
$legacy = Join-Path $msys '../lib/ossl-modules/legacy.dll'
if (-not (Test-Path $main) -or -not (Test-Path $loader) -or -not (Test-Path $objdump)) {
    throw 'Bibliotecas nativas ou objdump ausentes'
}
if (-not (Test-Path $legacy)) {
    throw 'Provedor OpenSSL legado ausente; NLA depende de MD4'
}

$seen = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
$queue = [System.Collections.Generic.Queue[string]]::new()
$queue.Enqueue($main)
while ($queue.Count -gt 0) {
    $current = $queue.Dequeue()
    $name = Split-Path $current -Leaf
    if (-not $seen.Add($name)) {
        continue
    }
    foreach ($line in (& $objdump -p $current)) {
        if ($line -match 'DLL Name:\s*(\S+)') {
            $dependency = Join-Path $msys $Matches[1]
            if (Test-Path $dependency) {
                $queue.Enqueue($dependency)
            }
        }
    }
}

$bundleDirectory = Join-Path $projectRoot 'src/main/resources/assets/ncat_minecraft/native/rdp'
New-Item -ItemType Directory -Force -Path $bundleDirectory | Out-Null
$bundle = Join-Path $bundleDirectory 'windows-x64.zip'
$stream = [System.IO.File]::Open($bundle, [System.IO.FileMode]::Create,
                                 [System.IO.FileAccess]::Write)
try {
    $archive = [System.IO.Compression.ZipArchive]::new($stream,
        [System.IO.Compression.ZipArchiveMode]::Create, $true)
    try {
        $seen.Add('ncatrdploader.dll') | Out-Null
        $seen.Add('legacy.dll') | Out-Null
        foreach ($name in ($seen | Sort-Object)) {
            $source = if ($name -ieq 'ncatrdp.dll' -or $name -ieq 'ncatrdploader.dll') {
                Join-Path $build $name
            } elseif ($name -ieq 'legacy.dll') {
                $legacy
            } else {
                Join-Path $msys $name
            }
            if (-not (Test-Path $source)) {
                throw "Dependência RDP ausente: $name"
            }
            $entry = $archive.CreateEntry($name,
                [System.IO.Compression.CompressionLevel]::Optimal)
            $input = [System.IO.File]::OpenRead($source)
            try {
                $output = $entry.Open()
                try {
                    $input.CopyTo($output)
                } finally {
                    $output.Dispose()
                }
            } finally {
                $input.Dispose()
            }
        }
    } finally {
        $archive.Dispose()
    }
} finally {
    $stream.Dispose()
}
Write-Output "Pacote RDP Windows: $bundle ($($seen.Count) bibliotecas)"

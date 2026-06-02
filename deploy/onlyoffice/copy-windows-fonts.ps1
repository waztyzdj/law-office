param(
  [switch] $All
)

$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$fontDir = Join-Path $scriptDir 'fonts'
New-Item -ItemType Directory -Force -Path $fontDir | Out-Null

$commonFontNames = @(
  'simsun.ttc',
  'simsunb.ttf',
  'SimsunExtG.ttf',
  'simhei.ttf',
  'simfang.ttf',
  'simkai.ttf',
  'msyh.ttc',
  'msyhbd.ttc',
  'msyhl.ttc',
  'Deng.ttf',
  'Dengb.ttf',
  'Dengl.ttf',
  'calibri.ttf',
  'calibrib.ttf',
  'calibrii.ttf',
  'calibril.ttf',
  'calibrili.ttf',
  'calibriz.ttf',
  'arial.ttf',
  'arialbd.ttf',
  'arialbi.ttf',
  'ariali.ttf',
  'times.ttf',
  'timesbd.ttf',
  'timesbi.ttf',
  'timesi.ttf'
)

$copied = 0
$fontsRoot = Join-Path $env:WINDIR 'Fonts'
$fontFiles = if ($All) {
  Get-ChildItem -LiteralPath $fontsRoot -File |
    Where-Object { $_.Extension.ToLowerInvariant() -in @('.ttf', '.ttc', '.otf') }
} else {
  foreach ($fontName in $commonFontNames) {
    $source = Join-Path $fontsRoot $fontName
    if (Test-Path -LiteralPath $source) {
      Get-Item -LiteralPath $source
    } else {
      Write-Warning "Font not found: $fontName"
    }
  }
}

foreach ($fontFile in $fontFiles) {
  if ($fontFile -and (Test-Path -LiteralPath $fontFile.FullName)) {
    Copy-Item -LiteralPath $fontFile.FullName -Destination $fontDir -Force
    $copied += 1
    Write-Host "Copied $($fontFile.Name)"
  }
}

Write-Host "Copied $copied font files to $fontDir"

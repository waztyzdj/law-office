param(
  [string] $ContainerName = 'onlyoffice-document-server',
  [switch] $Compose,
  [string] $ComposeFile
)

$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$fontDir = Join-Path $scriptDir 'fonts'
$fontAliasConfig = Join-Path $scriptDir 'fontconfig\law-office-font-aliases.conf'
$fontAliasScript = Join-Path $scriptDir 'apply-font-aliases.py'
$resolvedFontDir = Resolve-Path -LiteralPath $fontDir
$fontFiles = Get-ChildItem -LiteralPath $resolvedFontDir -File |
  Where-Object { $_.Extension -in @('.ttf', '.ttc', '.otf') }

if ($fontFiles.Count -eq 0) {
  throw "No font files found in $resolvedFontDir. Run .\copy-windows-fonts.ps1 or copy fonts manually first."
}

if (-not $ComposeFile) {
  $ComposeFile = Join-Path $scriptDir 'docker-compose.yml'
}

$generateFontsCommand = @'
set -e
python3 /usr/local/bin/law-office-apply-font-aliases.py --patch-generator
fc-cache -fv /usr/share/fonts/truetype/law-office /var/www/onlyoffice/Data/custom-fonts/law-office
documentserver-generate-allfonts.sh
python3 /usr/local/bin/law-office-apply-font-aliases.py
find /var/lib/onlyoffice/documentserver/App_Data/cache/files -mindepth 1 -exec rm -rf {} +
documentserver-flush-cache.sh || true
supervisorctl restart ds:docservice ds:converter || true
'@

if ($Compose) {
  docker compose -f $ComposeFile up -d
  docker compose -f $ComposeFile exec -T onlyoffice-document-server bash -lc $generateFontsCommand
  Write-Host 'ONLYOFFICE fonts initialized through Docker Compose.'
  exit 0
}

docker inspect $ContainerName | Out-Null
docker exec $ContainerName bash -lc 'mkdir -p /usr/share/fonts/truetype/law-office'
docker exec $ContainerName bash -lc 'mkdir -p /var/www/onlyoffice/Data/custom-fonts/law-office'
docker cp "$resolvedFontDir\." "${ContainerName}:/usr/share/fonts/truetype/law-office/"
docker cp "$resolvedFontDir\." "${ContainerName}:/var/www/onlyoffice/Data/custom-fonts/law-office/"
if (Test-Path -LiteralPath $fontAliasConfig) {
  docker cp $fontAliasConfig "${ContainerName}:/etc/fonts/conf.d/99-law-office-font-aliases.conf"
}
docker cp $fontAliasScript "${ContainerName}:/usr/local/bin/law-office-apply-font-aliases.py"
docker exec $ContainerName bash -lc $generateFontsCommand
Write-Host "ONLYOFFICE fonts initialized in container $ContainerName."

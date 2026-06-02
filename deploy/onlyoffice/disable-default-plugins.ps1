param(
  [string] $ContainerName = 'onlyoffice-document-server',
  [switch] $Compose,
  [string] $ComposeFile
)

$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $ComposeFile) {
  $ComposeFile = Join-Path $scriptDir 'docker-compose.yml'
}

$disablePluginsCommand = @'
set -e
plugins_root=/var/www/onlyoffice/documentserver/sdkjs-plugins
backup_dir="$plugins_root/law-office-disabled"
mkdir -p "$backup_dir"

find "$plugins_root" -maxdepth 1 -type d -name "{*}" -print |
while IFS= read -r item; do
  [ -e "$item" ] || continue
  base="$(basename "$item")"
  [ -e "$backup_dir/$base" ] || mv "$item" "$backup_dir/$base"
done

for item in "$plugins_root"/marketplace "$plugins_root"/v1; do
  [ -e "$item" ] || continue
  base="$(basename "$item")"
  [ -e "$backup_dir/$base" ] || mv "$item" "$backup_dir/$base"
done

for file_name in plugins.css plugins.css.gz plugin-list-default.json plugin-list-default.json.gz pluginBase.js; do
  [ -f "$plugins_root/$file_name" ] || continue
  [ -f "$backup_dir/$file_name" ] || cp "$plugins_root/$file_name" "$backup_dir/$file_name"
done

plugin_list="$plugins_root/plugin-list-default.json"
if [ -f "$plugin_list" ] && [ ! -f "$plugin_list.law-office.bak" ]; then
  cp "$plugin_list" "$plugin_list.law-office.bak"
fi
echo '[]' > "$plugin_list"
gzip -c "$plugin_list" > "$plugin_list.gz"
: > "$plugins_root/plugins.css"
gzip -c "$plugins_root/plugins.css" > "$plugins_root/plugins.css.gz"
chown -R ds:ds "$plugins_root"
chmod 444 "$plugin_list" "$plugin_list.gz" "$plugins_root/plugins.css" "$plugins_root/plugins.css.gz"

plugins_controller=/var/www/onlyoffice/documentserver/web-apps/apps/common/main/lib/controller/Plugins.js
if [ -f "$plugins_controller" ]; then
  [ -f "$plugins_controller.law-office.bak" ] || cp "$plugins_controller" "$plugins_controller.law-office.bak"
  if ! grep -q "law-office-hide-plugins-tab" "$plugins_controller"; then
    perl -0pi -e "s/('render:before'\s*:\s*function\s*\(toolbar\)\s*\{)/\$1\n                        \/\/ law-office-hide-plugins-tab\n                        return;/s" "$plugins_controller"
  fi
  gzip -c "$plugins_controller" > "$plugins_controller.gz"
  chown ds:ds "$plugins_controller" "$plugins_controller.gz"
  chmod 444 "$plugins_controller" "$plugins_controller.gz"
fi

documentserver-flush-cache.sh || true
supervisorctl restart ds:docservice ds:converter || true
'@

if ($Compose) {
  docker compose -f $ComposeFile up -d
  $disablePluginsCommand | docker compose -f $ComposeFile exec -T onlyoffice-document-server bash -s
  Write-Host 'ONLYOFFICE default plugins disabled through Docker Compose.'
  exit 0
}

docker inspect $ContainerName | Out-Null
$disablePluginsCommand | docker exec -i $ContainerName bash -s
Write-Host "ONLYOFFICE default plugins disabled in container $ContainerName."

# ONLYOFFICE 字体目录

把已授权使用的业务文档字体文件放在本目录。

Docker Compose 会把本目录挂载到 ONLYOFFICE Document Server：

```text
/usr/share/fonts/truetype/law-office
/var/www/onlyoffice/Data/custom-fonts/law-office
```

字体文件默认被 `.gitignore` 忽略，不要提交到代码仓库。部署到新服务器时，需要从授权来源重新准备字体文件，或通过部署平台的制品、对象存储、密钥文件等方式下发。

建议至少准备：

- 宋体 / 新宋体：`simsun.ttc`
- 黑体：`simhei.ttf`
- 仿宋：`simfang.ttf`
- 楷体：`simkai.ttf`
- 微软雅黑：`msyh.ttc`、`msyhbd.ttc`、`msyhl.ttc`
- 等线：`Deng.ttf`、`Dengb.ttf`、`Dengl.ttf`
- Calibri、Arial、Times New Roman 等常见英文字体

Windows 开发机可在 `deploy/onlyoffice` 下执行：

```powershell
.\copy-windows-fonts.ps1
```

复制当前 Windows 机器全部 `.ttf`、`.ttc`、`.otf` 字体：

```powershell
.\copy-windows-fonts.ps1 -All
```

字体放入本目录后，回到 `deploy/onlyoffice` 执行：

```powershell
.\init-fonts.ps1 -Compose
```

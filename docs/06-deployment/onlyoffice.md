# ONLYOFFICE Docs 部署说明

本文记录文档中心接入 ONLYOFFICE Docs 的部署流程。当前支持 PDF、Word、Excel、PPT 在线预览，支持 Word、Excel、PPT 在线协同编辑；历史版本接口已预留，版本控制下一阶段实现。

## 1. 部署目标

- 前端浏览器加载 ONLYOFFICE Document Server 的 `api.js`。
- 后端生成 ONLYOFFICE `view/edit` 配置，并提供短期 token 文件回源接口。
- Document Server 通过后端回源接口读取受保护文件，不直接暴露 MinIO 地址。
- 在线编辑保存时，Document Server 调用后端 callback，后端重新校验编辑权限后覆盖原对象存储内容。
- Document Server 容器内安装业务文档常用字体，保证预览和编辑时的字体、分页、行距尽量与本地 Office 一致。

## 2. 前置条件

部署机器需要：
- Docker 和 Docker Compose。
- 可访问后端服务的网络地址。
- 可访问 `onlyoffice/documentserver` 镜像，或提前准备好镜像。
- 已授权使用的字体文件。

操作机器需要：
- Windows 开发机执行项目内 PowerShell 脚本时，需要 PowerShell 和 Docker CLI。
- 操作机器不需要安装 Python。
- Python 要求在 ONLYOFFICE 容器内满足，因为 `apply-font-aliases.py` 是复制或挂载到容器内执行的脚本。

当前已验证 `onlyoffice/documentserver` 容器内存在 `python3`。如果未来换镜像后容器内没有 `python3`，需要基于 ONLYOFFICE 镜像制作自定义镜像安装 Python，或将 `deploy/onlyoffice/apply-font-aliases.py` 改写为 shell 脚本。

## 3. 字体准备

字体文件不提交到 Git。部署到新服务器时，必须从已授权来源重新准备字体文件，并放入：

```text
deploy/onlyoffice/fonts/
```

建议至少准备：
- 宋体 / 新宋体：`simsun.ttc`
- 黑体：`simhei.ttf`
- 仿宋：`simfang.ttf`
- 楷体：`simkai.ttf`
- 微软雅黑：`msyh.ttc`、`msyhbd.ttc`、`msyhl.ttc`
- 等线：`Deng.ttf`、`Dengb.ttf`、`Dengl.ttf`
- Calibri、Arial、Times New Roman 等常见英文字体

Windows 开发机可复制常用字体：

```powershell
cd deploy/onlyoffice
.\copy-windows-fonts.ps1
```

复制当前 Windows 机器全部 `.ttf`、`.ttc`、`.otf` 字体：

```powershell
cd deploy/onlyoffice
.\copy-windows-fonts.ps1 -All
```

生产部署时不要默认把个人电脑上的全部字体带到服务器，应按授权和业务需要准备字体包。

## 4. 启动 Document Server

推荐使用项目内置 Compose：

```powershell
cd deploy/onlyoffice
docker compose up -d
```

默认访问地址：

```text
http://localhost:8088
```

如需调整端口或 JWT 密钥，可通过环境变量覆盖：

```powershell
$env:ONLYOFFICE_PORT = "8088"
$env:ONLYOFFICE_JWT_SECRET = "lawoffice_onlyoffice_jwt_secret_2026_change_this"
docker compose up -d
```

Compose 会挂载：

```text
deploy/onlyoffice/fonts/ -> /usr/share/fonts/truetype/law-office
deploy/onlyoffice/fonts/ -> /var/www/onlyoffice/Data/custom-fonts/law-office
deploy/onlyoffice/fontconfig/law-office-font-aliases.conf
deploy/onlyoffice/apply-font-aliases.py
```

## 5. 初始化字体

字体文件放入 `deploy/onlyoffice/fonts/` 后，执行：

```powershell
cd deploy/onlyoffice
.\init-fonts.ps1 -Compose
```

脚本会在 ONLYOFFICE 容器内执行：

1. 检查 `fonts/` 是否存在 `.ttf`、`.ttc`、`.otf` 文件。
2. 挂载或复制 fontconfig 字体别名配置。
3. 执行 `fc-cache` 生成系统字体缓存。
4. 执行 `documentserver-generate-allfonts.sh` 生成 ONLYOFFICE 字体表。
5. 执行 `apply-font-aliases.py`，把 `仿宋_GB2312`、`宋体_GB2312`、`楷体_GB2312`、`黑体_GB2312` 等老文档字体名补进 ONLYOFFICE `AllFonts.js`。
6. 执行 `apply-font-aliases.py --patch-generator`，给 ONLYOFFICE 字体表生成脚本增加钩子，避免容器重启或重新生成字体表后丢失中文字体别名。
7. 清理 Document Server 旧转换缓存。
8. 重启 `docservice` 和 `converter`。

已有临时容器 `onlyoffice-document-server`，不是通过当前 Compose 启动时，可执行：

```powershell
cd deploy/onlyoffice
.\init-fonts.ps1
```

## 6. 禁用默认插件

默认 ONLYOFFICE 镜像会启用 YouTube、OCR、Translator、Mendeley、Zotero、AI 等插件，并在工具栏显示“插件”和“AI”页签。当前系统不开放这些能力，部署时需要禁用默认插件：

```powershell
cd deploy/onlyoffice
.\disable-default-plugins.ps1 -Compose
```

已有临时容器 `onlyoffice-document-server`，不是通过当前 Compose 启动时，可执行：

```powershell
cd deploy/onlyoffice
.\disable-default-plugins.ps1
```

脚本会把默认插件目录、`marketplace` 和 `v1` 目录备份到 `/var/www/onlyoffice/documentserver/sdkjs-plugins/law-office-disabled/`，将默认插件列表改为空数组，重新生成 gzip 文件；同时会给 `/var/www/onlyoffice/documentserver/web-apps/apps/common/main/lib/controller/Plugins.js` 增加带 `law-office-hide-plugins-tab` 标记的补丁，阻止 ONLYOFFICE 无条件添加“插件”页签。脚本最后会重启 `docservice` 和 `converter`。已打开的编辑弹窗不会实时变化，需要关闭后重新打开。

## 7. 后端配置

后端配置文件：

```text
backend/src/main/resources/application.yml
backend/src/main/resources/application-dev.yml
backend/src/main/resources/application-prod.yml
```

核心配置：

| 配置 | 说明 | 本地示例 |
| --- | --- | --- |
| `ONLYOFFICE_ENABLED` | 是否启用 ONLYOFFICE 接口 | `true` |
| `ONLYOFFICE_DOCUMENT_SERVER_URL` | 浏览器访问 Document Server 的地址，也是保存回调下载地址的可信来源 | `http://localhost:8088` |
| `ONLYOFFICE_SERVER_BASE_URL` | Document Server 回源访问后端的地址，必须包含 `/api` | `http://host.docker.internal:8080/api` |
| `ONLYOFFICE_JWT_SECRET` | 与 Document Server `JWT_SECRET` 一致，至少 32 字节 | `lawoffice_onlyoffice_jwt_secret_2026_change_this` |
| `ONLYOFFICE_PREVIEW_TOKEN_MINUTES` | 文件回源短期 token 有效分钟数 | `10` |
| `ONLYOFFICE_CALLBACK_TOKEN_MINUTES` | 在线编辑保存回调 token 有效分钟数，需覆盖用户一次编辑会话 | `1440` |
| `ONLYOFFICE_RENDER_VERSION` | 渲染缓存版本。字体或字体别名变更后递增 | `fonts-20260602-alias3` |

重要约束：
- `ONLYOFFICE_JWT_SECRET` 必须与 Document Server 的 `JWT_SECRET` 一致。
- `ONLYOFFICE_SERVER_BASE_URL` 是 Document Server 容器访问后端的地址，不是浏览器访问后端的地址。
- Docker Desktop 本地开发可使用 `http://host.docker.internal:8080/api`。
- Linux 生产环境通常不能依赖 `host.docker.internal`，应配置为容器能访问到的后端内网域名、服务名或宿主机网关地址。
- `ONLYOFFICE_DOCUMENT_SERVER_URL` 需要与 ONLYOFFICE 回调里的保存文件 URL 同源。后端会校验保存下载 URL 的 host 和 port，避免回调 token 被用于访问任意 URL。
- 字体有调整后，除重新执行 `init-fonts.ps1` 外，还要递增 `ONLYOFFICE_RENDER_VERSION`，强制旧文档重新转换。

## 8. 前端行为

前端通过后端接口获取配置：

```text
GET /files/document/onlyoffice/config/{fileId}?mode=view
GET /files/document/onlyoffice/config/{fileId}?mode=edit
```

返回体包含：
- `documentServerApiUrl`：需要动态加载的 ONLYOFFICE `api.js` 地址。
- `config`：传给 `new DocsAPI.DocEditor(...)` 的配置。

编辑模式会在配置中包含 `editorConfig.callbackUrl`，Document Server 保存时回调：

```text
POST /files/document/onlyoffice/callback/{token}
```

回调地址必须能被 Document Server 访问。后端收到 `status=2` 或 `status=6` 时，会重新校验用户编辑权限，然后下载回调里的文件并覆盖原对象存储内容。

前端每次打开预览或编辑都会重新加载 `api.js` 并附加缓存参数，避免浏览器继续使用旧的 ONLYOFFICE 静态资源 hash。

## 9. 验证步骤

部署或迁移后按顺序检查：

1. 访问 `http://<document-server-host>:8088`，确认 ONLYOFFICE 欢迎页可打开。
2. 执行 `.\init-fonts.ps1 -Compose` 后确认输出没有字体生成错误。
3. 检查容器服务：

```powershell
docker exec onlyoffice-document-server supervisorctl status
```

核心服务应为：

```text
ds:docservice RUNNING
ds:converter  RUNNING
```

4. 从 Document Server 容器内确认能访问后端回源地址：

```powershell
docker exec onlyoffice-document-server bash -lc "curl -I http://host.docker.internal:8080/api"
```

生产环境把 URL 换成实际 `ONLYOFFICE_SERVER_BASE_URL` 的主机部分。

5. 登录系统，在文档中心双击 Word/PDF/Excel/PPT 文件，确认在线预览打开。
6. 对有编辑权限的 Word/Excel/PPT 文件点击“在线编辑”，使用两个浏览器用户打开同一文件，确认可协同编辑。
7. 编辑后关闭编辑器或等待自动保存，确认文件更新时间变化，再重新打开文件确认内容已保存。
8. 使用包含 `仿宋_GB2312`、`宋体_GB2312` 等字体的 Word 文件验证字体和分页。
9. 如果预览或编辑卡在“文件加载中”，查看日志：

```powershell
docker logs onlyoffice-document-server --tail 300
docker exec onlyoffice-document-server bash -lc "tail -120 /var/log/onlyoffice/documentserver/docservice/out.log; tail -120 /var/log/onlyoffice/documentserver/converter/out.log"
```

## 10. 迁移到新服务器清单

1. 准备 Docker / Docker Compose。
2. 准备 `deploy/onlyoffice/fonts/` 字体文件，字体文件不来自 Git。
3. 设置 Document Server `JWT_SECRET`。
4. `docker compose up -d` 启动 ONLYOFFICE。
5. 执行 `.\init-fonts.ps1 -Compose` 初始化字体。
6. 执行 `.\disable-default-plugins.ps1 -Compose` 禁用默认插件和 AI 入口。
7. 配置后端 `ONLYOFFICE_*` 参数，尤其是 `DOCUMENT_SERVER_URL`、`SERVER_BASE_URL`、`JWT_SECRET`、`CALLBACK_TOKEN_MINUTES`。
8. 如果字体包或字体别名发生变化，递增 `ONLYOFFICE_RENDER_VERSION`。
9. 重启后端。
10. 按“验证步骤”跑一遍真实文件预览和编辑保存。

## 11. 安全边界

- `/files/document/onlyoffice/config/{fileId}` 需要当前登录 JWT。
- 后端生成 ONLYOFFICE 配置前会校验文档中心读取或更新权限。
- `/files/document/onlyoffice/download/{token}` 不使用浏览器 JWT，只接受后端生成的短期 token。
- `/files/document/onlyoffice/callback/{token}` 不使用浏览器 JWT，只接受后端生成的编辑回调 token，并按 ONLYOFFICE 约定返回 `{"error":0}` 或 `{"error":1}`。
- 回源 token 包含文件 ID、租户 ID 和过期时间；回调 token 额外包含编辑用户，用于保存时重新校验更新权限。
- 后端不暴露 MinIO 直链。
- 当前部署脚本会禁用 ONLYOFFICE 默认插件列表，因此工具栏不显示“插件”和“AI”页签。
- `mode=edit` 只对 Word、Excel、PPT 开放，并由后端按文档中心 `canUpdate` 权限最终裁决；PDF 仍只读预览。
- 编辑模式的 `document.key` 按文件当前最终保存版本生成。`status=6` 强制保存不刷新版本时间，确保编辑中的用户加入同一协同会话；`status=2` 最终保存刷新版本时间，确保下一次编辑进入新版本。
- 历史版本接口当前仅预留，下一阶段再保存 ONLYOFFICE 回调中的版本数据和变更文件。

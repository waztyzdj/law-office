# ONLYOFFICE Docs 部署说明

本文记录文档中心接入 ONLYOFFICE Docs 的部署流程。当前阶段支持 PDF、Word、Excel、PPT 在线预览；后续在线编辑仍基于同一套 Document Server、字体和回源配置扩展。

## 1. 部署目标

- 前端浏览器加载 ONLYOFFICE Document Server 的 `api.js`。
- 后端生成只读预览配置，并提供短期 token 文件回源接口。
- ONLYOFFICE Document Server 通过后端回源接口读取受保护文件，不直接暴露 MinIO 地址。
- Document Server 容器内安装业务文档常用字体，保证预览和后续编辑的字体、分页、行距尽量与本地 Word/Office 一致。

## 2. 前置条件

部署机器需要：

- Docker 和 Docker Compose。
- 可访问后端服务的网络地址。
- 可访问 `onlyoffice/documentserver` 镜像，或提前准备好镜像。
- 已授权使用的字体文件。

操作机器需要：

- Windows 开发机执行项目内 PowerShell 脚本时，需要 PowerShell 和 Docker CLI。
- 操作机器不需要安装 Python。
- Python 要求在 ONLYOFFICE 容器内满足，因为 `apply-font-aliases.py` 是复制或挂载到容器内执行的脚本，用于修改容器内的 ONLYOFFICE 字体表。

已验证当前 `onlyoffice/documentserver` 容器内存在 `python3`。如果未来换镜像后容器内没有 `python3`，需要基于 ONLYOFFICE 镜像制作自定义镜像安装 Python，或将 `deploy/onlyoffice/apply-font-aliases.py` 改写为 shell 脚本。

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

生产部署时不要默认把个人电脑上的全部字体带到服务器；应按授权和业务需要准备字体包。

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

## 6. 后端配置

后端配置项位于：

```text
backend/src/main/resources/application.yml
backend/src/main/resources/application-dev.yml
backend/src/main/resources/application-prod.yml
```

核心配置：

| 配置 | 说明 | 本地示例 |
| --- | --- | --- |
| `ONLYOFFICE_ENABLED` | 是否启用 ONLYOFFICE 预览接口 | `true` |
| `ONLYOFFICE_DOCUMENT_SERVER_URL` | 浏览器访问 Document Server 的地址 | `http://localhost:8088` |
| `ONLYOFFICE_SERVER_BASE_URL` | Document Server 回源访问后端的地址，必须包含 `/api` | `http://host.docker.internal:8080/api` |
| `ONLYOFFICE_JWT_SECRET` | 与 Document Server `JWT_SECRET` 一致，至少 32 字节 | `lawoffice_onlyoffice_jwt_secret_2026_change_this` |
| `ONLYOFFICE_PREVIEW_TOKEN_MINUTES` | 文件回源短期 token 有效分钟数 | `10` |
| `ONLYOFFICE_RENDER_VERSION` | 渲染缓存版本。字体或字体别名变更后递增 | `fonts-20260602-alias3` |

重要约束：

- `ONLYOFFICE_JWT_SECRET` 必须与 Document Server 的 `JWT_SECRET` 一致。
- `ONLYOFFICE_SERVER_BASE_URL` 是 Document Server 容器访问后端的地址，不是浏览器访问后端的地址。
- Docker Desktop 本地开发可使用 `http://host.docker.internal:8080/api`。
- Linux 生产环境通常不能依赖 `host.docker.internal`，应配置为容器能访问到的后端内网域名、服务名或宿主机网关地址。
- 字体有调整后，除重新执行 `init-fonts.ps1` 外，还要递增 `ONLYOFFICE_RENDER_VERSION`，强制旧文档重新转换。

## 7. 前端行为

前端通过后端接口获取：

```text
GET /files/document/onlyoffice/config/{fileId}?mode=view
```

返回体只包含：

- `documentServerApiUrl`：需要动态加载的 ONLYOFFICE `api.js` 地址。
- `config`：传给 `new DocsAPI.DocEditor(...)` 的配置。

前端每次打开预览都会重新加载 `api.js` 并附加缓存参数，避免浏览器继续使用旧的 ONLYOFFICE 静态资源 hash。

## 8. 验证步骤

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
6. 使用包含 `仿宋_GB2312`、`宋体_GB2312` 等字体的 Word 文件验证字体和分页。
7. 若预览卡在“文件加载中”，查看日志：

```powershell
docker logs onlyoffice-document-server --tail 300
docker exec onlyoffice-document-server bash -lc "tail -120 /var/log/onlyoffice/documentserver/docservice/out.log; tail -120 /var/log/onlyoffice/documentserver/converter/out.log"
```

## 9. 迁移到新服务器清单

新服务器部署时按此清单执行：

1. 准备 Docker / Docker Compose。
2. 准备 `deploy/onlyoffice/fonts/` 字体文件，字体文件不来自 Git。
3. 设置 Document Server `JWT_SECRET`。
4. `docker compose up -d` 启动 ONLYOFFICE。
5. 执行 `.\init-fonts.ps1 -Compose` 初始化字体。
6. 配置后端 `ONLYOFFICE_*` 参数，尤其是 `DOCUMENT_SERVER_URL`、`SERVER_BASE_URL`、`JWT_SECRET`。
7. 如果字体包或字体别名发生变化，递增 `ONLYOFFICE_RENDER_VERSION`。
8. 重启后端。
9. 按“验证步骤”跑一遍真实文件预览。

## 10. 安全边界

- `/files/document/onlyoffice/config/{fileId}` 需要当前登录 JWT。
- 后端生成 ONLYOFFICE 配置前会校验文档中心读取权限。
- `/files/document/onlyoffice/download/{token}` 不使用浏览器 JWT，只接受后端生成的短期 token。
- 回源 token 包含文件 ID、租户 ID 和过期时间。
- 后端不暴露 MinIO 直链。
- 当前阶段 `editorConfig.mode` 固定为 `view`，不允许编辑保存。

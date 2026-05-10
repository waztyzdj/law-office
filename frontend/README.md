# LawOffice 前端 - 纯净版 Vben Admin

## 项目说明

这是一个从 `vue-vben-admin` 提取的纯净版 Vben Admin 前端项目，已移除以下内容：

- ❌ Element UI 相关依赖
- ❌ Naive UI 相关依赖
- ❌ TDesign 相关依赖
- ❌ Mock 数据服务
- ❌ 测试配置（Vitest、Playwright）
- ❌ 文档站点
- ❌ Playground 示例
- ❌ 其他不必要的应用

## 保留内容

- ✅ Ant Design Vue 4.x
- ✅ Vben Admin 核心功能包（@vben/*）
- ✅ Vue 3 + TypeScript
- ✅ Pinia 状态管理
- ✅ Vue Router 路由
- ✅ Vite 构建工具
- ✅ ESLint + Prettier + Stylelint 代码规范
- ✅ Tailwind CSS 样式方案

## 目录结构

```
frontend/
├── packages/              # Vben 核心包（Monorepo）
│   ├── @core/            # 核心基础包
│   ├── effects/          # 功能效果包
│   ├── constants/        # 常量定义
│   ├── icons/            # 图标组件
│   ├── locales/          # 国际化
│   ├── preferences/      # 偏好设置
│   ├── stores/           # 状态管理
│   ├── styles/           # 样式系统
│   ├── types/            # TypeScript 类型
│   └── utils/            # 工具函数
├── internal/             # 内部工具配置
│   └── lint-configs/     # Lint 配置
├── src/                  # 应用源码
│   ├── adapter/          # 适配器
│   ├── api/              # API 接口
│   ├── layouts/          # 布局组件
│   ├── locales/          # 国际化配置
│   ├── router/           # 路由配置
│   ├── store/            # Pinia Store
│   └── views/            # 页面视图
├── package.json          # 项目配置
├── pnpm-workspace.yaml   # Workspace 配置
├── vite.config.ts        # Vite 配置
└── tsconfig.json         # TypeScript 配置
```

## 快速开始

### 环境要求

- Node.js >= 20.0.0
- pnpm >= 9.0.0

### 安装依赖

```bash
pnpm install
```

### 开发模式

```bash
pnpm dev
```

### 构建生产版本

```bash
pnpm build
```

### 预览构建结果

```bash
pnpm preview
```

### 类型检查

```bash
pnpm typecheck
```

## 技术栈

- **框架**: Vue 3.5+ (Composition API)
- **UI 库**: Ant Design Vue 4.2+
- **构建工具**: Vite 8.0+
- **状态管理**: Pinia 3.0+
- **路由**: Vue Router 5.0+
- **HTTP 客户端**: Axios
- **工具库**: @vueuse/core, dayjs, lodash-es
- **样式**: Tailwind CSS 4.2+
- **代码规范**: TypeScript 6.0+, ESLint 10.0+

## 注意事项

1. **Workspace 配置**: 本项目采用 pnpm workspace 管理 Monorepo，请勿手动修改 `packages/` 下的依赖版本
2. **路径别名**: `@vben/*` 已配置指向本地 packages，确保在 `tsconfig.json` 和 `vite.config.ts` 中保持一致
3. **API 对接**: 请根据实际后端接口修改 `src/api/` 目录下的接口定义
4. **环境变量**: 开发环境变量在 `.env.development`，生产环境变量在 `.env.production`

## 后续优化建议

1. 根据业务需求精简 `packages/` 中未使用的模块
2. 自定义主题配置在 `src/preferences.ts`
3. 配置后端代理在 `vite.config.ts`
4. 添加业务专属的工具函数到 `packages/utils`

## 许可证

MIT License

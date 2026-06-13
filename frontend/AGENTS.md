# 前端 AI 编码规范

> 适用范围：本文件约束 `frontend/` 下所有 Vue 3 + TypeScript + Ant Design Vue + Vben Admin 代码。后续 AI 或开发者在修改前端代码前，必须先阅读并遵守本文件。

## 维护原则

- 本规范优先级高于临时个人偏好；若与用户明确需求冲突，以用户需求为准，并在本文件补充新的项目约定。
- 先读现有代码，再写新代码。优先复用 `src/`、`packages/`、`@vben/*`、`#/` 中已有模式，不另起一套风格。
- 每次引入稳定的新实践、修复重复踩坑的问题、或调整目录/抽象边界时，都要同步更新本文件。
- 当前技术基线参考 `package.json`：Vue `3.5.x`、Ant Design Vue `4.2.x`、Vben Admin `5.7.x`、Pinia `3.x`、Vue Router `5.x`。
- 官方实践参考：
  - Vue Style Guide: https://vuejs.org/style-guide/
  - Vue TypeScript with Composition API: https://vuejs.org/guide/typescript/composition-api
  - Vben Standards: https://doc.vben.pro/en/guide/project/standard.html
  - Ant Design Vue: https://antdv.com/docs/vue/introduce

## AI 工作流

1. 修改前先用 `rg` / `rg --files` 查找同类实现，确认目录边界、命名、组件组合方式和 API 封装方式。
2. 优先做最小范围修改。不要顺手重构无关模块，不要改动用户已有未关联变更。
3. 涉及 UI 时必须同时考虑 loading、empty、error、disabled、权限、长文本、窄屏和重复提交状态。
4. 涉及表单/接口时必须做类型约束、输入校验、异常兜底和提交态锁定。
5. 完成后至少运行 `pnpm.cmd typecheck`；影响构建、路由、全局样式或依赖时再运行 `pnpm.cmd build`。
6. 修改前必须先判断影响面：普通业务页面按当前页面验证；公共组件、公共组合函数、请求层、权限、路由、store、全局样式等横向能力改动，必须按公共能力变更验证，不能只验证当前需求页面。
7. 如果发现现有规范缺口，补充到本文件的“项目约定”或“持续完善清单”。

## 目录边界

- `src/views/`：业务页面。按领域分组，如 `src/views/system/user/`。页面入口只负责组装，复杂逻辑下沉到 `hooks/`、`components/`、`utils/`。
- `src/api/`：业务接口和 DTO 类型。一个业务模块一个文件，接口调用统一走 `#/framework/api/request` 或 `BaseApi`。
- `src/framework/api/`：项目请求基建。不要在业务页面绕过这里直接创建 HTTP 客户端。
- `src/composables/`：项目级可复用组合函数，例如通用表格能力。不能写入强业务耦合逻辑。
- `src/store/`：应用级 Pinia store。认证、用户、全局偏好等跨页面状态放这里；页面局部状态留在页面或 hooks。
- `src/router/`：路由和权限守卫。菜单、权限、懒加载组件必须保持现有路由模式。
- `src/adapter/`：Vben 与当前 UI 框架的适配层。表单、校验、组件映射优先使用这里的封装。
- `packages/`：可跨应用复用的基础能力。禁止依赖 `src/views` 或具体业务 API。
- 业务需求默认不要修改 `packages/`。只有当问题确认为跨应用公共能力缺陷、且无法在 `src/` 内通过组合/封装解决时，才允许改动 `packages/`，并必须说明影响范围和验证方式。
- `internal/`：构建、lint、格式化配置。除非任务明确要求，不修改工程配置。

## 业务模块文件结构

当前系统管理模块已经形成稳定结构，新增或重写业务管理页必须沿用该结构，不允许把页面、表格、表单、接口和流程逻辑随意混写在一个文件中。标准 CRUD/管理页目录如下：

```text
src/views/<domain>/<module>/
  index.vue
  components/
    <Module>Table.vue
    <Module>FormDrawer.vue
    <Module>ExtraDrawer.vue      # 可选：授权、分配、详情等独立流程
  hooks/
    use<Module>Table.ts
    use<Module>Columns.ts
```

配套文件必须放在对应边界内：

```text
src/api/<domain>/<module>.ts      # DTO、BaseApi 实例、接口包装方法
src/router/routes/modules/*.ts    # 路由入口，保持现有领域路由组织
src/constants/permissions.ts      # 权限码常量
```

- `index.vue` 只负责页面组装：调用 `use<Module>Table`，维护子组件 ref，处理 `handleAdd`、`handleEdit`、`handleSaveSuccess` 等跨组件事件。不要在 `index.vue` 中写表格列、表单 schema、分页筛选细节或大段接口流程。
- `<Module>Table.vue` 只负责列表呈现和事件派发：接收 `dataSource`、`loading`、`pagination`、`activeFilters` 等窄 props，使用 `BaseTable` 或项目既有表格封装，工具栏按钮通过 emits 通知父级。不要在表格组件内直接请求列表、保存筛选缓存或刷新页面数据。
- `use<Module>Table.ts` 负责表格状态和流程：封装 `useTable` 配置、分页、筛选、删除确认、loading、localStorage key 和 `loadData`。不要在这里写 Vue 模板渲染、表单 schema 或抽屉生命周期。
- `use<Module>Columns.ts` 负责列定义：使用 `defineTableColumns`，声明列宽、筛选类型、对齐、操作列和权限判断。除操作列必要的 `customRender` 外，不要在列配置里塞入复杂业务流程；复杂交互通过 emit 回到页面入口。
- `<Module>FormDrawer.vue` 负责新增/编辑浮层：使用 `useVbenDrawer + useVbenForm`，包含 schema 构造、初始值、详情回填、校验、payload 清洗、提交锁定和 `success` 事件。保存成功后只 `emit('success')`，由页面入口刷新列表。
- 授权、角色分配、详情查看等非主表单流程放入独立 `<Module>ExtraDrawer.vue` 或语义明确的子组件，必须通过 `open(payload)` 暴露命令式入口，并通过窄 props/emits 与页面通信。
- `src/api/<domain>/<module>.ts` 必须集中维护该模块 DTO、`BaseApi` 实例和导出的包装方法。组件和 hooks 默认只导入这些包装方法，不直接拼 URL；特殊接口可以单独导出，但仍留在 API 文件中。
- 模块根目录默认只放 `index.vue`。除 `components/`、`hooks/` 外，只有在存在两个以上本模块文件真实复用时，才允许新增 `utils.ts`、`constants.ts` 或 `types.ts`；新增时必须说明职责，不能成为杂物文件。
- 单文件超过职责边界时优先按上述结构拆分，而不是新增一个“万能组件”或把公共能力提前抽到 `src/composables/`。只有两个以上模块稳定复用后，才考虑上移公共层。
- 特殊结构页面可以偏离模板，例如树形菜单、字典主从表、日志只读列表，但偏离点必须局部且有业务原因；仍要保留“入口组装、组件呈现、hooks 管流程、API 层封装”的边界。

## 命名规范

- Vue 组件文件使用 PascalCase：`UserTable.vue`、`UserFormDrawer.vue`。
- 页面入口可使用 `index.vue`，页面内子组件必须有业务语义名。
- 组合函数使用 `useXxx.ts`，返回值保持稳定且显式命名，如 `useUserTable`。
- API 方法使用动词 + 资源：`pageUsers`、`getUserById`、`saveUser`、`deleteUser`。
- 类型/接口使用 PascalCase，避免 `I` 前缀：`UserInfo`、`BasePageReq`。
- 常量使用 camelCase 导出对象或 UPPER_SNAKE_CASE 单值，权限码统一维护在 `src/constants/permissions.ts`。
- localStorage key 必须带模块前缀，如 `user_list_filters`，避免全局冲突。

## Vue 3 与 TypeScript

- SFC 默认使用 `<script setup lang="ts">`。只有需要 Options API 兼容时才使用 `defineComponent`。
- `defineProps`、`defineEmits` 必须声明类型；Vue 3.3+ 推荐 emits tuple 写法：

```ts
const emit = defineEmits<{
  success: [];
  edit: [record: UserInfo];
}>();
```

- props 有默认值时使用 reactive props destructure 或 `withDefaults`；数组、对象默认值不能共享引用。
- `ref` 初始值无法准确推断时必须传泛型，例如 `ref<UserInfo>()`、`ref<DrawerExpose | null>(null)`。
- 避免 `any`。确实需要时限定在边界层，并用 `unknown`、类型守卫或 DTO 转换逐步收窄。
- 派生状态使用 `computed`，不要用 `watch` 同步出另一个普通状态。
- `watch` 只用于副作用，并明确 `immediate`、`deep`、清理逻辑；异步 watch 要处理过期请求或组件卸载。
- 不直接修改 props。需要本地编辑态时复制到 `ref/reactive`，保存时再清洗 payload。
- `defineExpose` 只暴露必要的命令式方法，如抽屉 `open`；不要暴露内部状态。

## 组件设计

- 页面入口负责“组装”：加载 hooks、连接子组件、处理跨组件事件。
- 业务子组件负责“呈现和事件”：通过 props 接收数据，通过 emits 通知父级，不直接调用页面级 API，除非它本身就是独立业务容器。
- hooks 负责“状态和流程”：表格加载、筛选、删除、提交、权限推导等可测试逻辑放到 hooks。
- 组件 props 保持窄接口。不要把整页上下文对象传给子组件。
- 所有列表项必须有稳定 key；Ant Design Vue Table 必须设置 `row-key`。
- 所有异步操作必须有 loading/disabled/lock 状态，避免重复提交。
- DOM 事件处理函数命名用 `handleXxx`；对外事件命名用业务动作，如 `success`、`edit`、`delete`。

## Ant Design Vue 与 Vben

- 优先使用 Vben 已有封装：`useVbenForm`、`useVbenDrawer`、访问控制指令、`@vben/common-ui` 等。
- Ant Design Vue 组件从 `ant-design-vue` 按需导入；不要自行封装一套重复基础组件。
- 表格优先使用 `src/composables/Table` 的 `useTable`、`defineTableColumns`、列筛选与列宽持久化能力。
- 使用 `defineTableColumns` 时必须传入模块唯一的 `tableKey`，例如 `{ tableKey: 'system_user' }`。列宽持久化会按 `tableKey:dataIndex` 隔离，禁止多个列表共用默认列宽命名空间，避免 `action`、`status` 等同名列互相污染宽度。
- Table 必须配置：`row-key`、`loading`、`pagination`、必要的 `scroll`，长列设置宽度并允许省略提示。
- 系统表格表头统一居中；表体按字段语义对齐：普通文本左对齐、数值右对齐、状态/枚举/下拉选项/日期时间/操作列居中。通用 `defineTableColumns` 默认遵循该规则，手写 Ant Design Vue Table 必须显式配置列对齐和表头居中。
- 操作列固定在右侧，关闭筛选：`fixed: 'right'`、`hasFilter: false`。
- 表单优先使用 schema 配置和 `#/adapter/form` 中的校验工具；必填、长度、格式、跨字段校验必须在前端声明。
- 新增/编辑表单字段顺序必须按业务识别优先级组织：父级/归属上下文在最前，编码、账号、ID 等稳定唯一标识先于名称/显示文本，核心分类和状态随后，排序字段靠近基础信息，联系方式、地址、描述、备注等补充信息放后面；列表列顺序应与同模块表单的主字段顺序保持一致。
- 抽屉/弹窗提交时使用 `drawerApi.lock()` / `unlock()` 或等价状态，`finally` 中释放锁。
- 删除、导入导出等高风险操作必须二次确认；列表页默认不提供批量删除和刷新按钮，成功后的数据刷新由页面内部自动处理，不作为显式工具按钮暴露。
- 权限控制统一使用 `v-access:code`、`useAccess` 和 `permissionCodes`，不要在模板里硬编码权限字符串。

## API 与数据边界

- 业务接口类型和 API 方法放在 `src/api/<domain>/<module>.ts`。
- 常规 CRUD 优先使用 `BaseApi`，特殊接口再单独导出函数。
- 不把后端原始响应直接扩散到页面深处。必要时在 API 层或 store 层做字段适配。
- 请求参数必须符合后端分页/筛选协议：`pageNum`、`pageSize`、`queryParams`、`sortField`、`sortOrder`。
- API 方法不要吞异常；页面/组合函数负责提示，底层请求层负责统一错误处理。
- 保存 payload 前必须清理空字符串、确认密码等非业务字段，复用 `cleanFormPayload`。
- 文件导入导出必须处理 Blob、文件名、错误提示和 loading 状态。

## 状态管理

- 页面私有状态放在页面或 hooks，跨页面状态才进入 Pinia。
- store 使用 setup store 写法，action 中封装异步流程，避免组件重复拼流程。
- 登录、登出、用户信息、权限码必须保持当前 `useAuthStore`、`@vben/stores`、路由守卫协作模式。
- store 中不要保存可由服务端重新获取的大体积临时数据，除非有明确缓存策略。
- 修改 store 时必须考虑 `$reset`、登出清理和登录过期逻辑。

## 路由、菜单与权限

- 新页面路由必须使用懒加载组件，保持 `src/router/routes` 现有组织方式。
- 路由 meta 中的标题、图标、权限、缓存、隐藏菜单等配置要与菜单生成逻辑兼容。
- 菜单图标统一使用项目已接入的 `@vben/icons` / Iconify 图标体系，优先使用 `lucide:*`，管理端图标选择必须来自 `src/constants/menu-icons.ts` 白名单，不新增图标库或让用户自由输入不可验证的图标名。
- 不在组件内部绕过路由守卫做全局权限跳转；权限判断统一放守卫或访问控制工具。
- 新增权限码时同步更新 `src/constants/permissions.ts` 和后端权限数据/SQL（如任务涉及）。

## 样式与 UI

- 优先使用现有 Vben/Tailwind/Ant Design Vue token 和项目样式变量。
- 业务组件样式默认 `scoped`；全局样式只放在 `src/styles` 或约定的全局入口。
- 不在模板中堆大量内联 style。可复用样式抽成 class。
- 页面布局保持后台管理系统风格：信息密度适中、可扫描、少装饰、操作路径清晰。
- 抽屉、弹窗等浮层内容要按业务复杂度设置合适宽度和最大高度；列表、树、穿梭框等大块控件应填满可用内容宽度但限制高度，避免在宽屏或少量数据时出现大面积留白。
- 新增/编辑抽屉优先使用固定宽度，不要随意用百分比宽度；同类单列表单统一一个宽度标准，复杂表单优先参考 `UserRoleDrawer` 的 `sm:w-[760px]! sm:max-w-none!`，避免机构、租户等页面各自漂移。
- 列表页首个表格卡片不要再额外添加 `margin-top`，顶部留白统一由页面容器 `padding` 控制；同类页面的工具条、卡片、表格之间只保留一套标准间距，避免每个模块自己再叠一层空白。
- `Switch`、`Checkbox`、`Radio` 等紧凑型控件不能继承输入框的整行宽度；在使用通用表单 `w-full` 配置时必须为这类控件单独设置自然宽度。
- 不使用会破坏 Ant Design Vue 交互状态的深层覆盖；必须覆盖时限制选择器作用域。
- 所有可变宽文本必须考虑溢出：表格列用省略和 Tooltip，按钮和标签避免被长文本撑坏。
- 响应式布局使用断点、栅格、弹性布局和稳定尺寸，避免由内容加载造成明显布局跳动。

## 可靠性与可维护性

- 异步函数必须用 `try/finally` 保证 loading/lock 释放；需要用户提示时用 `message`/`notification` 保持一致。
- 浏览器 API（`localStorage`、`document`、`window`）访问必须考虑异常和 SSR/测试环境兼容。
- 复杂条件、枚举和状态映射抽成常量，避免模板中出现魔法数字。
- 删除、批量操作、权限、认证、导入导出属于高风险逻辑，必须优先保证正确性和可回滚。
- 公共函数必须保持小而稳定。只有两个以上模块真实复用，或能明显降低复杂度时才抽到公共层。
- 注释解释“为什么”，不要复述“做了什么”。乱码注释或过时注释应在触碰相关代码时修复。

## 导入与依赖

- 使用已有路径别名：`#/` 指向 `src/`，`@vben/*` 指向 workspace packages。
- 类型导入使用 `import type`。
- 导入顺序保持现有风格：类型、Vue、第三方、Vben、项目别名、相对路径。
- 不新增依赖，除非已有能力无法满足且收益明确；新增依赖必须更新 workspace 配置并说明原因。
- 不在业务代码里直接引用 `packages` 内部深路径，优先使用包导出的公开入口。

## 验证标准

- 普通 TS/Vue 修改：运行 `pnpm.cmd typecheck`。
- 影响构建配置、路由、全局注册、依赖、样式入口：运行 `pnpm.cmd build`。
- 影响 UI 交互：启动本地服务并用浏览器检查主要流程、窄屏、长文本和空状态。
- 修改公共组件、公共组合函数、请求层、权限、路由、store 或全局样式：必须先说明影响面，并至少抽样验证 2-3 个现有调用方没有类型和行为回归；抽样应覆盖当前业务页面、一个系统管理页和一个不同业务模块页面。
- 修改 `BaseTable`、`useTable`、`defineTableColumns` 或表格全局样式时，必须重点验证表格分页、筛选、排序、操作列、权限按钮、横向滚动和空状态；不能只依赖 `pnpm.cmd typecheck`。
- 修改 `BaseApi`、请求封装、路由守卫或权限工具时，必须验证至少一个已有列表页和一个需要权限控制的操作入口。
- 无法运行验证时，最终回复必须说明原因和剩余风险。

## 项目约定

- 用户、角色、菜单等管理页采用“入口 `index.vue` + `components/` + `hooks/`”结构。
- 列表页工具条默认只保留新增、筛选和必要的单条操作，不提供批量删除和刷新按钮；如确有需要，单独按需求设计，不与通用列表默认行为混用。
- 用户管理抽屉可作为参考模板，但不建议把整套表单 schema 抽成强通用组件；优先复用 `useVbenDrawer + useVbenForm` 的打开、预填、同步、校验、锁定提交、关闭回填这条生命周期链。只有当多个业务抽屉在宽度、标题、动作区和提交流程都高度一致时，才抽共享壳子或 `useFormDrawer` 类组合式能力。
- 树形列表、TreeSelect、授权树等通用树操作优先复用 `src/composables/Tree/useTree.ts`，包括树数据加载、选项转换、节点过滤、展开 key 和子孙 key 收集；构造树数据时叶子节点不要补空 `children`，否则树表会把它们当成可展开节点；业务接口、校验、提交和权限判断仍留在页面或业务组件中。
- 表格筛选状态可持久化到 localStorage，但 key 必须模块唯一，且读取失败时安全返回空对象。表格列宽持久化必须通过 `defineTableColumns` 的 `tableKey` 隔离，同一业务列表的 `tableKey` 要稳定，不得随路由参数、分页或筛选条件变化。
- `BaseApi` 实例方法如果依赖 `this`，传给组合函数时必须用箭头函数或导出的包装方法，避免上下文丢失。
- 用户表单当前使用 `UserFormDrawer.vue` 模式：打开前准备 schema 和初始值，打开后同步表单，提交后清洗 payload 并刷新列表。
- 涉及数据库字段枚举、状态码或字段含义时，必须先参考 `sql/建表脚本.sql` 对应字段注释；前端展示文案、表单选项、TS 类型和 API payload 不得自行定义与建表脚本不一致的含义。
- 后续遇到中文显示乱码时，优先确认文件真实编码，不要盲目重写整文件。
- 前端所有列表和编辑表单默认不展示创建人、创建时间、修改人、修改时间、删除人、删除时间等审计字段；只有明确的业务需求才允许单独显式展示，并优先通过通用列/表单配置统一处理，避免逐页散落实现。

## 持续完善清单

- 新增业务模块后，把可复用的表格、表单、权限、字典、导入导出模式沉淀到本文件。
- 如果后端接口协议变化，同步更新“API 与数据边界”和 `src/framework/api/README.md`。
- 如果恢复单元测试或端到端测试，补充测试目录、命名和最低覆盖要求。
- 如果引入新的 UI 组件封装，说明何时用 Vben 封装、何时直接用 Ant Design Vue。

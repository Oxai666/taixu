# 古龙相对上游太墟的完整改动说明

> **基线**：`Oxai666/taixu` @ `955082fd`（上游 `taixu-lab/taixu` main，2026-11-01）
> **本分支**：`gulong`，31 个提交
> **规模**：336 个文件，`+3415 / −1385`
> **产物**：`top.wkbin.gulong.debug`，应用名「古龙」，约 55.9 MB

---

## 一、总览

三类改动，一句话各说清：

| 类别 | 内容 |
|---|---|
| **新增能力** | 外部 Termux 通道（工具 + 设置页）、账户余额查询、思考强度六档与其专属图形、悬浮小窗的工具回执与思考展示、图片全屏预览 |
| **改掉行为** | 应用身份（包名/应用名）、思考强度位置与适配（DeepSeek/智谱）、图片附件不再落沙箱、提示词对齐实际行为 |
| **删掉的东西** | 整套自更新、debug 包的 LeakCanary 桌面入口、图片的沙箱副本、「太墟」品牌字样 |

---

## 二、新增

### 2.1 新模块 `:gulong:termux`（7 个文件）

外部 Termux 调用通道。**这是与内置 PRoot 沙箱并列的第二套执行环境**，跑的是用户手机上装的 Termux。

| 文件 | 职责 |
|---|---|
| `TermuxClient.kt` | RUN_COMMAND 协议：`state()` / `verify()` / `runCommandCapture()`、marker 包裹 argv、启动 ACK 与真完成区分、超时与拒权归类 |
| `TermuxDefaults.kt` | 6 项配置的默认值与夹取上下限（命令超时 60s/5–600、验证 8s/3–30、stdout 8000/1000–64000、stderr 2000/500–16000、工作目录、apt 包装开关） |
| `TermuxRuntime.kt` | @Volatile 运行时持有器，工具侧无协程上下文也能读到最新值 |
| `TermuxSettingsStore.kt` | SharedPreferences 持久化，写入即夹取再同步进 Runtime |
| `TermuxText.kt` | UTF-8 码点边界安全截断、POSIX 单引号转义、nohup 脱离包装 |

### 2.2 harness 层接线（3 个文件）

| 文件 | 职责 |
|---|---|
| `TermuxToolBackend.kt` | 执行后端：前置检查、apt/dpkg 非交互前导、后台脱离、失败返回可行动的 recovery |
| `TermuxToolDefinition.kt` | 工具 schema（`termux`）。单独成文件的直接原因：`ProviderClient` 有 1284 行棘轮，唯一可增长的方式是抽出去 |
| `ProviderBalanceClient.kt` | 余额查询：探测链 + 用户自定义路径优先 |

### 2.3 UI 新增（4 个文件）

| 文件 | 职责 |
|---|---|
| `TermuxSettingsScreen.kt` | Termux 设置页：状态 / 超时 / 默认值与限制 / 设置帮助 四区 |
| `ModelBalanceFields.kt` | 模型档案里的余额接口路径 + 取值路径两个输入框 |
| `ReasoningLevelGlyph.kt` | 思考强度的档位专属图形（面板 Hero 的终态）+ 索引/配色映射 |
| `ChatReasoningOpener.kt` | `LocalReasoningOpener`：跨层传递「打开思考强度面板」 |
| `ImagePreviewOverlay.kt` | 图片全屏预览 |
| `FloatingToolBubbles.kt` | 悬浮小窗里的工具回执 / 思考气泡 / 用量行 |

### 2.4 数据与构建

| 项 | 说明 |
|---|---|
| `MIGRATION_85_86` | `harness_models` 加 `balanceApiPath` / `balanceResultPath` 两列，DB 85→86 |
| `app/src/debug/AndroidManifest.xml` | 摘掉 LeakCanary 的 launcher 入口（`activity-alias`） |
| `.github/workflows/gulong-build.yml` | 古龙构建线：push 即出 debug APK + artifact |

---

## 三、修改

### 3.1 应用身份

| 项 | 改前 | 改后 |
|---|---|---|
| applicationId | `top.wkbin.taixu[.dev]` | `top.wkbin.gulong[.dev]`（debug 落 `.debug`） |
| 应用名 | 太墟 / TaiXuDev | 古龙 / 古龙预览 |
| namespace | `top.wkbin.taixu` | **不变**（Java 包路径，不是安装标识，改动面为零） |
| 品牌字样 | 「太墟」180 文件 422 处 | 全量替换为「古龙」；另补 html/js/css/sh/py/svg 94 文件 957 处 |

### 3.2 思考强度

- 从顶栏移到输入区「+」左侧（`ComposerQuickActions`）
- 六档：关闭 / 低 / 中 / 高 / 超高 / 极限 ↔ Off / Low / Medium / High / xHigh / Max
- `ReasoningEffort` 枚举补 `XHIGH`（原先只有 LOW/MEDIUM/HIGH/MAX，界面上的「超高」映射成 null = 强度直接丢失）
- 按厂商分别适配：
  - **DeepSeek**（`reasoning_effort` 只有 none/low/high/max）：低→low，中/高→high，超高/极限→max，关闭→none + `thinking.type=disabled`
  - **智谱 GLM**：`thinking.type` + `thinking_budget`（超高 6000）
  - OpenAI 官方仍压到 high（其 API 只认 low/medium/high）
- 面板：300ms 防穿透（点开瞬间的 up 事件会穿到遮罩上）、选中不再自动关
- 按钮图标：从通用大脑改为「面板 Hero 的当前档位终态」+ 中间图标（关闭=大脑，其余=星火）

### 3.3 图片附件

| 项 | 改前 | 改后 |
|---|---|---|
| 落盘位置 | `filesDir/linux-runtime/attachments`（沙箱 `/attachments`） | 图片 → `cacheDir` 临时文件，压缩后即删 |
| `guestFilePath` / `hostFilePath` | 指向沙箱文件 | `null` |
| 消息提示词 | 「[附件：已复制并挂载到 Linux 沙箱]…请用 base 读取」 | 纯图片消息**零文本**，只发 `image_url` |
| 文档附件 | 进沙箱 | **不变**（Agent 需要 read） |

### 3.4 悬浮小窗

- 不再 `filter { it !is ToolResult }`（原来工具回执全被丢掉）
- 新增：工具回执气泡（成功/失败配色 + 耗时 + 输出前 600 字）、思考过程气泡（前 800 字）
- 两者默认收起，点标题才展开（窗只有 310dp 宽，全展开会把正文挤没）
- 正文改用 `MarkdownText`（原先是纯文本 `Text`）
- 底部新增用量行：`上下文 N · 缓存 N · 输出 N · 3.4s`
- 浏览器状态栏新增「AI 小窗」入口，`minimizeApp=false`（原实现会 `moveTaskToBack`，一点就回桌面）

### 3.5 提示词

- Termux 工具描述补三条：**两个环境文件系统互不相通**、选型规则（真机包用 termux / 构建用 base）、失败按 `recovery` 字段行动
- base 工具描述补反向指引：要操作真机 Termux 用 termux，两者不通
- 图片附件提示词从「挂载到沙箱 + 用 base 读」改为零文本

---

## 四、删除

| 项 | 位置 | 说明 |
|---|---|---|
| 自更新整套 | `AboutCommunityScreen` 两个入口 + `MainActivity` 启动检查 | `AppUpdateManager` 注入点保留以免打断 Koin 装配，`updateInfo` 恒为 null，所有更新弹窗自然不再触发 |
| LeakCanary 桌面图标 | `app/src/debug/AndroidManifest.xml` | 那是 `activity-alias` 不是 `activity`，写错节点类型时 merger 会静默不删 |
| 图片的沙箱副本 | `ChatAttachment` | 见 3.3 |

**一行代码没删的**：内置 PRoot 沙箱、termux-terminal-emulator、浏览器引擎、子智能体系统、工作流、MCP、Shizuku 通道——全部原样保留。

---

## 五、已知限制（没做或做不到的）

| 项 | 状态 |
|---|---|
| APK 里 1 处「太墟」 | `classes9.dex` 偏移 1052200，原文「通用工程请在太墟终端中执行自定义命令或自定义构建脚本」。全仓 5064 文件（含 jar/aar 解压）零命中，判断来自下载的依赖 aar，代码层改不掉 |
| DeepSeek 只有 4 档 | 其 `reasoning_effort` 只有 none/low/high/max，「中」和「高」在它上面效果相同，「超高」和「极限」也相同。服务商能力上限，非实现偷懒 |
| OpenAI 官方压 xHigh | 其 API 只认 low/medium/high |
| 余额货币符号 | 写死 `¥`。DeepSeek 是人民币，OpenAI 是美元，中转站有的返 credits 不返钱 |
| 余额无缓存 | 每次打开模型面板重查一次（几百毫秒）。Ultra 那边做了 2 分钟缓存，这边没做 |
| 余额探测链 | 未覆盖 OpenAI 官方 `/dashboard/billing/credit_grants`（需特殊 key 类型） |
| 浏览器小窗 | 复用系统级悬浮窗（覆盖所有界面），不是 Ultra 那种页面内嵌 `BrowserMiniChat`。要内嵌版需另做 |

---

## 六、棘轮成本记录

这个仓库有 `.architecture-baseline.json` 行数棘轮（只许下调）与 `architecture-policy.json` 依赖登记。本次 31 个提交里有 14 个是修 CI，其中：

- 依赖未登记 ×1（`:gulong:termux`）
- 行数棘轮 ×7（ToolExecutor / ProviderClient / ChatToolCards / TaiXuNavHost / FloatingChatView / ModelEditorScreen / SettingsViewModel / ChatMessageBubbles）
- 穷尽 `when` 漏分支 ×3（ApprovalPolicyEngine / ChatToolCards / ToolStepHeader 等 9 处）
- 资源放错模块 ×1（`chat_action_termux` 放进了 app 而不是 feature:chat）
- 参数加错函数重载 ×2（`saveModels` 单数 vs 复数、`UpsertRequest` 漏字段）

**做法上的教训**：改大文件前先查基线余量（`SettingsViewModel` 1829、`ModelEditorScreen` 1315、`ChatScreen` 901、`ChatMessageBubbles` 1032 这些余量都是 0，加一行得先腾一行）；加枚举前先扫全仓穷尽 `when`；加字段前先把穿透的每一层（UI → ViewModel → Request → Entity → 迁移）列全。

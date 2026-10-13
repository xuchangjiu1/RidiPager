# RidiPager —— 翻页器蓝牙遥控阅读器

给 RIDI-KO（瑞芯微 Android 电子书）用的翻页 App。  
**核心功能：配对蓝牙翻页器后，按翻页键就能上下翻页。支持 TXT / EPUB / PDF。**

---

## 一、工作原理（先搞懂，省的怀疑）

你的翻页器（HBTR002 这类）配对后，**系统把它当成一个蓝牙键盘**。  
你按一下键，它发的是标准按键（PageUp / PageDown / 音量键 / 方向键…）。

所以 App 根本不需要"主动连蓝牙读遥控指令"——**只要接收系统派发的按键事件**就行。

本 App 做的三件事：

1. 全屏接管所有常见的翻页按键
2. 按一下就翻一页
3. 显示页码

**好处：完全通用。** 不管是 HBTR002、别的翻页器、蓝牙键盘、遥控器，只要发标准翻页键，都能用。

---

## 二、按键映射表（已内置，不用配置）

| 你按的键              | 动作        |
| ----------------- | --------- |
| PageUp / PageDown | 上一页 / 下一页 |
| 方向键 左·上 / 右·下     | 上一页 / 下一页 |
| **音量键 上 / 下**     | 上一页 / 下一页 |
| 媒体键 上一曲 / 下一曲     | 上一页 / 下一页 |
| 回车 / 空格           | 下一页       |
| 数字键 1 / 2         | 上一页 / 下一页 |

> 淘宝多数翻页器默认发的是**音量键**，所以这条最关键——已在 dispatch 层拦截，不会弹出系统音量条。

---

## 三、支持的格式

| 格式                  | 支持 | 说明                                   |
| ------------------- | -- | ------------------------------------ |
| **TXT**             | ✅  | 自动识别 UTF-8 / GBK / BOM 编码            |
| **EPUB**            | ✅  | 解析章节结构，按章阅读，跨章自动衔接                   |
| **PDF**             | ✅  | 系统 `PdfRenderer` 逐页渲染，翻页器翻的正好是 PDF 页 |
| Ridibooks 加密书 (.rb) | ❌  | DRM 保护，破解属侵权，不做                      |

### EPUB 实现细节（零第三方库）

自己写的 ZIP + XML 解析：

```
META-INF/container.xml  →  定位 OPF
*.opf                   →  manifest(id→href) + spine(阅读顺序)
*.xhtml                 →  剥离标签、解码 HTML 实体，得到纯文本
```

- 支持 `../` 相对路径解析
- 支持 HTML 实体（`&nbsp;` `&emsp;` `&#123;` `&#x1F;` 等）
- 每章独立分页，**翻到章末自动进入下一章**，往回翻到章首自动跳到上一章末页

### PDF 实现细节

用 Android 内置 `PdfRenderer`（API 21+），**不需要任何第三方库**：

- 按屏幕宽度等比渲染，避免大图爆内存
- `LruCache` 缓存最近 6 页，来回翻页不重复渲染
- **专用后台渲染线程**（`HandlerThread`）——因为 `PdfRenderer` 非线程安全且同一时刻只能开一页
- 渲染时先显示"正在渲染…"，完成后自动重绘

---

## 四、怎么装到设备上

### 路线 A：有电脑 + 数据线（推荐）

**第 1 步：设备打开 USB 调试**  
`设置 → 关于设备 → 连点"版本号"7 次` → 返回 `设置 → 开发者选项 → 打开"USB 调试"`

**第 2 步：电脑装 adb**  
下载 platform-tools：<https://developer.android.com/studio/releases/platform-tools>  
解压后在该目录执行：

```bash
adb devices        # 设备上勾选"始终允许"
adb install -r RidiPager-debug.apk
```

### 路线 B：无电脑

把 APK 拷进设备存储卡 → 用文件管理器点它安装 →  
（若提示"未知来源"，去 `设置 → 安全 → 允许安装未知来源应用` 打开）

---

## 五、怎么构建 APK

### 用 Android Studio（最省事）

1. 装 Android Studio（自带 JDK + SDK）
2. `File → Open` 选择 `RidiPager` 目录
3. 等 Gradle 同步 → `Build → Build APK(s)`
4. 产物：`app/build/outputs/apk/debug/RidiPager-debug.apk`

### 命令行

需要 **JDK 17** + **Android SDK**：

```bash
./gradlew assembleDebug
```

---

## 六、使用步骤

1. 装好 App，打开
2. 系统设置里**配对蓝牙翻页器**（配一次即可，以后自动连）
3. 点「打开电子书」选文件（TXT / EPUB / PDF）
   - 也可以什么都不做——App 启动时会**自动扫描** `/sdcard` 常见目录并加载第一本书
4. 按翻页器按键 → 翻页 ✅

底部状态栏显示：`[格式] 第 X / Y 页 · 第 M/N 章 | 翻页器已就绪`

---

## 七、文件结构

```
RidiPager/
├── app/src/main/
│   ├── AndroidManifest.xml              # 权限：蓝牙、读文件
│   ├── java/com/ridipager/app/
│   │   ├── MainActivity.kt              # 主界面 + 按键拦截（核心）
│   │   ├── KeyMapper.kt                 # 按键映射表（核心）
│   │   ├── ReaderView.kt                # 阅读视图，三种模式统一翻页接口
│   │   ├── Paginator.kt                 # TXT 分页引擎 + 编码识别
│   │   ├── EpubParser.kt                # EPUB 解析（手写，零依赖）
│   │   └── PdfDocument.kt               # PDF 渲染（PdfRenderer + 专用线程）
│   └── res/
├── probe_pager.sh                       # 翻页器按键探测脚本
└── README.md
```

---

## 八、技术要点（可能的坑，已处理）

| 坑                   | 处理方式                                      |
| ------------------- | ----------------------------------------- |
| 音量键被系统音量条抢          | `dispatchKeyEvent` 优先拦截 + `onKeyDown` 双保险 |
| EPUB/PDF 解析耗时导致 ANR | 解析全程在后台线程，结果回主线程应用                        |
| `PdfRenderer` 非线程安全 | 专用 `HandlerThread` 串行渲染，`@Volatile` 标志防竞态 |
| 快速翻页导致图片错位          | 渲染回调里比对页码，只在页码未变时刷新                       |
| 老设备 API 兼容          | `minSdk 21`；PDF 不可用时给出明确提示                |
| 中文 TXT 乱码           | 依次尝试 UTF-8 严格解码 → BOM → GBK → Latin1      |

---

## 九、常见问题

**Q：按翻页器没反应？**  
A：① 确认翻页器已在系统里配对成功；② 确认 App 在前台；③ 跑 `probe_pager.sh` 看按键码，告诉我我加进映射表。

**Q：按了变成调音量？**  
A：已处理——本 App 在 dispatch 层拦截音量键，不会弹音量条。

**Q：翻页器是 BLE 自定义协议，不是键盘模式？**  
A：那需要在 App 里写 BLE GATT 客户端主动接收。告诉我，我按协议加模块。

**Q：EPUB 图片/样式丢失？**  
A：当前版本提取的是**纯文字内容**（专注阅读与翻页体验）。图文混排 EPUB 需要额外做渲染，可以后续加。

**Q：PDF 能选中文字吗？**  
A：不能。Android 没有公开的 PDF 文本抽取 API，本方案是**渲染成图片**。但翻页器翻的正好是"页"，阅读体验一致。

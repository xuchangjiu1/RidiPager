# 最简单方案：GitHub 云端编译（不用装任何软件）

> 适合：电脑小白、不想装 Android Studio、只想拿到 APK
> 原理：把代码传到 GitHub，云端自动编译，你下载 APK 就行

---

## 为什么推荐这个方案

| 对比 | GitHub 云端 | 装 Android Studio |
|---|---|---|
| 要下载的大小 | **几乎为 0** | 1GB 安装包 + 3GB SDK |
| 等待时间 | 传代码 1 分钟 + 编译 5 分钟 | 下载 1-2 小时 |
| 操作难度 | 网页点几下 | 装软件 + 配环境 |
| 费用 | **免费** | 免费（但费时间） |
| 电脑配置要求 | **无** | 需要 8GB+ 内存 |

---

## 准备工作（一次性）

### 1. 注册 GitHub 账号
1. 打开 **https://github.com**
2. 点 **Sign up**
3. 填邮箱、密码、用户名 → 完成验证

> 如果你已经有 GitHub 账号，跳过这步。

### 2. 装 GitHub Desktop（图形界面，不用敲命令）
1. 打开 **https://desktop.github.com**
2. 点 **Download for Windows**
3. 双击安装，一路 Next
4. 打开后用你的 GitHub 账号登录

> 为什么用这个？因为它是**图形界面**，不用打命令，适合小白。

---

## 上传代码（第一次）

### 第 1 步：新建仓库
1. 打开 GitHub Desktop
2. 菜单 **File → New repository...**
3. **Name** 填：`RidiPager`
4. **Local path** 点 **Choose...**，选到：
   ```
   C:\Users\Administrator\WorkBuddy\2026-10-13-23-28-45
   ```
5. **不要**勾 "Initialize this repository with a README"
6. 点 **Create repository**

> ⚠️ 如果提示该目录已有文件，选 **Yes** 继续。

### 第 2 步：确认要上传的文件
1. GitHub Desktop 左侧会列出**很多文件**
2. 这些是我们的工程文件，**全部保留**
3. 左下角 **Summary** 填：`RidiPager 翻页器阅读器`
4. 点 **Commit to main**

### 第 3 步：上传到云端
1. 点顶部的 **Publish repository**
2. **取消勾选** "Keep this code private"（必须公开！私有仓库构建要收费）
3. 点 **Publish repository**
4. 等它上传完（几秒到几十秒）

---

## 云端编译（自动的）

### 第 4 步：看着它自动编译
1. 上传完成后，浏览器打开你的仓库页面：
   ```
   https://github.com/你的用户名/RidiPager
   ```
2. 点顶部 **Actions** 标签
3. 会看到一个正在跑的构建任务（黄色圆点 🟡）
4. 等它变成**绿色对勾 ✅**（约 3-8 分钟）

> 🟡 进行中 → ✅ 成功 → ❌ 失败

### 第 5 步：下载 APK
1. 点进那个 **绿色对勾 ✅** 的构建
2. 页面拉到底部，找到 **Artifacts** 区域
3. 点 **RidiPager-APK** 下载
4. 会下载一个 `RidiPager-APK.zip`
5. 解压它，里面就是 **`RidiPager-debug.apk`** ⭐

---

## 装到 RIDI-KO 上

### 第 6 步：连电脑拷文件
1. 数据线连 RIDI-KO 到电脑
2. 打开 `此电脑` → 双击 **RIDI-KO**
3. 进去找 **Download** 或 **下载** 文件夹
4. 把 `RidiPager-debug.apk` 拖进去

### 第 7 步：在阅读器上安装
1. 拔数据线
2. 在 RIDI-KO 上打开**文件管理器**
3. 进入 **Download** 文件夹
4. 点 `RidiPager-debug.apk`
5. 提示"未知来源"→ 去设置里允许 → 再装

### 第 8 步：接翻页笔
```
① OTG 转接头插进 RIDI-KO
② 翻页笔的 USB 接收器插进 OTG
③ 打开 RidiPager，按翻页键 → 翻页 ✅
```

---

## 遇到问题

### GitHub Actions 显示红色 ❌（构建失败）
1. 点进失败的构建
2. 点左侧的 **build** 步骤
3. 展开**报错的那一步**（通常有红色 ✗）
4. **把报错内容截图或复制给我**，我帮你改

### 上传时提示文件太大
工程代码很小（不到 100KB），不会出现这问题。
如果真出现，告诉我。

### 不想公开代码怎么办
私有仓库的 Actions 有免费额度（每月 2000 分钟），
我们的构建一次约 5 分钟，够用 **400 次**。可以放心用私有。

> 但如果私有仓库提示需要信用卡，就改成公开。

### 能不能改代码后重新构建
可以！改完代码后在 GitHub Desktop 里：
1. 看到变更 → **Commit to main**
2. **Push origin**（右上角）
3. 云端自动重新构建
4. 去 Actions 页下载新的 APK

---

## 流程图（一图看懂）

```
你的电脑                     GitHub 云端                RIDI-KO
─────────                   ────────────               ────────
写完代码
  │
  ▼
GitHub Desktop
Commit + Push  ──────────→  自动触发构建
                              │
                              ▼
                          装 JDK + SDK
                          编译 APK
                              │
                              ▼
                          上传 Artifact
                              │
  ┌───────────────────────────┘
  ▼
下载 APK.zip
解压
  │
  ▼
拖动 APK 到 RIDI-KO  ──────────────────────────────→  安装
                                                        │
                                                        ▼
                                                    插上翻页笔
                                                    按一下 → 翻页 ✅
```

---

## 💡 我的建议

**先试 GitHub 方案。** 它最省事，你不用装任何软件。

如果卡在哪一步，**直接问我**，把截图发我，我帮你解决。

**如果 GitHub 方案也搞不定**，那就退回装 Android Studio 的方案（见我给你的另一个手册）。

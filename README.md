# Wallpaper — 一键静默换壁纸

一个"反直觉"的轻量 Android 原生壁纸应用。它不做壁纸库、不搞社区，只专注一件事：**把换壁纸变成零成本的操作**——点击桌面图标就是换壁纸，像按电梯按钮一样简单。

---

## 项目介绍

### 它解决什么问题

常见的换壁纸应用要求你：打开 App → 翻列表 → 点预览 → 点应用，一次操作要 5~6 步。这个应用反其道而行：**点击应用图标直接静默换壁纸**，全程无界面、无弹窗、无通知；长按图标进入设置页，其余一切交给系统自动完成。

### 核心设计

- **统一入口**：点击图标、外链、定时任务、磁贴全部汇入同一个换壁纸管线（`WallpaperChanger.change`），行为完全一致
- **静默无打扰**：换壁纸由透明跳板 + 后台服务完成，零界面、零通知、零 Toast，结果只写入壁纸记录
- **缓存秒换**：应用启动 / 每次换壁纸后自动预取，点击瞬间从本地缓存应用，不等待网络请求
- **取旧补新**：缓存按 FIFO 队列运转——每次换壁纸移出最旧一张、同步补充一张新图到队尾，缓存永远满员
- **数据透明**：缓存与历史壁纸存放于外部可见目录，PNG/JPG 原图可直接用文件管理器查看、拷贝或删除

### 功能特性

- **一键静默换壁纸**：点击应用图标即换壁纸，无弹窗、无通知、无界面跳转
- **多入口统一**：外链 `wallpaper://change`、定时任务、磁贴「上一张壁纸」均走同一管线
- **缓存预取（秒换）**：后台自动预取壁纸（默认 5 张，可配 3/5/10/20/50/100），换壁纸时从缓存读取，不再等待网络
- **图片 API 自定义**：任意直接返回图片字节流的接口，支持主屏 / 锁屏 / 主屏+锁屏三种模式
- **定时切换**：30 分钟 ~ 24 小时可调，AlarmManager 精确唤醒（`setExactAndAllowWhileIdle`），开机自恢复，无权限时自动降级
- **壁纸记录**：记录每次换壁纸结果（成功/失败 + 缩略图），保留条数可配（10/30/50/100）
- **缓存/记录画廊**：缓存与历史双 Tab 直接内联展示缩略图，**单击某张图即设为壁纸，长按保存原图到相册**
- **格式校验**：文件头魔数检测真实格式（webp/gif 自动转码 jpg），防止损坏文件进入缓存
- **后台保护引导**：电池优化白名单 / 自启动管理一键跳转（适配国产 ROM）

### 适用场景

- **自动党**：配合定时切换（30 分钟 ~ 24 小时），每天解锁手机都是新壁纸
- **自建源玩家**：有任意返回图片字节流的 API，即可自定义壁纸来源
- **极简主义者**：厌恶弹窗与通知打扰，只想"按一下换壁纸"
- **壁纸收藏党**：缓存壁纸留档可查，换过的每一张都能在记录页回溯缩略图

---

## 安装指南

### 方式一：直接安装 APK（推荐）

1. 前往 [GitHub Releases](https://github.com/Ckey1225/one-tap-wallpaper/releases) 下载最新版本 APK
2. 将 APK 传输到手机（或直接用浏览器下载）
3. 点击安装，若提示"未知来源"请在系统设置中允许安装该应用

### 方式二：命令行构建（需 Android SDK + JDK 17）

```bash
# 构建 Debug 包
./gradlew assembleDebug

# 构建 Release 包（需在 local.properties 配置签名证书）
./gradlew assembleRelease
```

产物路径：

- Debug：`app/build/outputs/apk/debug/app-debug.apk`
- Release：`app/build/outputs/apk/release/app-release.apk`

> 仓库已配置阿里云 Maven 镜像，国内网络可直接拉取依赖。

### 构建要求

| 依赖 | 版本 |
| --- | --- |
| Android SDK | 34（compileSdk） |
| JDK | 17 |
| 最低系统 | Android 7.0（API 24，支持单独设置锁屏壁纸） |

### 签名说明

Release 构建使用正式 keystore 签名。签名信息保存在**不入库**的 `local.properties` 中：

```properties
STORE_FILE=/path/to/your-release.jks
STORE_PASSWORD=***
KEY_ALIAS=***
KEY_PASSWORD=***
```

未配置时自动回退 debug 签名，便于开发调试。

---

## 使用说明

> **第一步**：长按应用图标 → 「设置」磁贴 → 进入设置页 → 填写 **图片 API 地址**。
> 该地址需要是一个**直接返回图片字节流的 URL**，例如 `https://t.alcy.cc/pc/`。

### 基本操作

| 操作 | 效果 |
| --- | --- |
| 点击应用图标 | 静默换壁纸 |
| 长按图标 →「设置」 | 进入设置页 |
| 长按图标 →「上一张壁纸」 | 静默回到上一张用过的壁纸 |
| 浏览器打开 `wallpaper://change` | 静默换壁纸 |
| 设置页 →「立即换壁纸」 | 手动换壁纸 |
| 缓存/记录页单击某张图 | 将该图直接设为壁纸 |
| 缓存/记录页长按某张图 | 保存原图到相册 |

### 设置页分区（底部导航）

| 分区 | 内容 |
| --- | --- |
| **换壁纸** | 图片 API 地址、壁纸模式（主屏/锁屏/主屏+锁屏）、立即换壁纸按钮 |
| **缓存** | 内联展示待用缓存壁纸列表（长按保存到相册） |
| **记录** | 内联展示已应用历史壁纸列表（长按保存到相册） |
| **设置** | 定时切换开关与间隔、电池优化白名单/自启动、记录保留条数、缓存数量与手动补充 |

### 常见问题

- **换壁纸没反应？** 请确认图片 API 地址正确且能直接返回图片字节流（可先用浏览器打开该地址验证）。
- **定时任务不触发？** 请在「设置」分区将应用加入**电池优化白名单**并开启**自启动**（国产 ROM 必须，否则进程可能被杀）。
- **缓存目录在哪？** `Android/data/com.example.wallpaper/files/wallpapers/`，其中 `cache/` 为待用队列、`applied/` 为已应用历史。

---

## 技术栈

- Kotlin + Jetpack Compose（Material 3）
- OkHttp（图片下载，指数退避重试）
- Storage Access Framework（`DocumentFile` 统一文件访问）
- AlarmManager（定时切换，`setExactAndAllowWhileIdle` + 权限降级）
- SharedPreferences（配置与壁纸记录持久化）
- Kotlin Coroutines（IO 调度，防 ANR）

## 目录结构

```
app/src/main/java/com/example/wallpaper/
├── MainActivity.kt           # 透明跳板 Activity（点击图标 / 外链 / 磁贴入口）
├── WallpaperApp.kt           # Application（注册磁贴、启动预取缓存）
├── data/                     # 数据层：网络、配置持久化、缓存队列
│   ├── NetworkModule.kt      # OkHttp 单例
│   ├── PreferenceStore.kt    # SharedPreferences 配置 + 壁纸记录
│   ├── WallpaperCache.kt     # FIFO 缓存队列（File 后端，格式校验）
│   └── WallpaperRepository.kt# 图片下载（指数退避重试 + 采样解码）
├── domain/                   # 领域层：换壁纸统一入口
│   ├── WallpaperChanger.kt   # 缓存优先 → 网络兜底（唯一核心方法）
│   ├── WallpaperSetter.kt    # WallpaperManager 设置（主屏/锁屏/双屏）
│   └── WallpaperTarget.kt    # 壁纸目标枚举
├── permission/               # 权限引导
│   └── PermissionGuide.kt    # 电池优化白名单 / 自启动管理
├── schedule/                 # 定时调度
│   ├── WallpaperScheduler.kt # AlarmManager 精确/宽松调度
│   ├── QuickChangeAlarmReceiver.kt
│   └── BootReceiver.kt       # 开机自恢复定时任务
├── shortcut/                 # 磁贴 + 静默服务
│   ├── ShortcutHelper.kt     # 长按图标磁贴（设置 / 上一张壁纸）
│   └── QuickWallpaperService.kt # 静默换壁纸后台服务
└── ui/                       # 界面层（Compose）
    ├── settings/             # 设置页（底部导航 4 分区）
    ├── log/                  # 缓存/记录画廊（WallpaperGallery）
    └── theme/                # Material 3 主题
```

## 开源协议

[MIT](LICENSE)
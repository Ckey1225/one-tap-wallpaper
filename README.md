# Wallpaper — 一键静默换壁纸

一个"反直觉"的轻量 Android 原生壁纸应用。它不做壁纸库、不搞社区，只专注一件事：**把换壁纸变成零成本的操作**——点击桌面图标就是换壁纸，像按电梯按钮一样简单。

## 它解决什么问题

常见的换壁纸应用要求你：打开 App → 翻列表 → 点预览 → 点应用，一次操作要 5~6 步。这个应用反其道而行：**点击应用图标直接静默换壁纸**，全程无界面、无弹窗、无通知；长按图标进入设置页，其余一切交给系统自动完成。

## 核心设计

- **静默无打扰**：换壁纸全程零界面、零通知、零 Toast，结果只写入壁纸记录
- **缓存秒换**：后台自动预取 2~5 张壁纸排队，点击瞬间从本地缓存应用，不等待网络请求
- **取旧补新**：缓存按 FIFO 队列运转——每次换壁纸移出最旧一张、同步补充一张新图到队尾，缓存永远满员
- **统一入口**：图标点击、浏览器外链、定时任务全部走同一条换壁纸管线，行为完全一致
- **数据透明**：缓存目录默认位于 `Android/data/<包名>/files/wallpapers`，PNG/JPG 原图可直接用文件管理器查看、拷贝或删除

## 适用场景

- **自动党**：配合定时切换（30 分钟 ~ 24 小时），每天解锁手机都是新壁纸
- **自建源玩家**：有任意返回图片字节流的 API，即可自定义壁纸来源
- **极简主义者**：厌恶弹窗与通知打扰，只想"按一下换壁纸"
- **壁纸收藏党**：缓存壁纸留档可查，换过的每一张都能在记录页回溯缩略图

## 技术亮点

- **Kotlin + Jetpack Compose (Material 3)**，底部导航分区设置页，Monet 动态取色（Android 12+）
- **OkHttp** 图片下载，指数退避重试
- **SAF (DocumentFile) 双后端缓存**：默认公共目录 + 自定义目录，文件头魔数校验真实格式（webp/gif 自动转码 jpg）
- **AlarmManager** 精确定时唤醒（`setExactAndAllowWhileIdle`），开机自恢复
- **壁纸记录**带缩略图回溯，保留条数可配（10/30/50/100）

## 功能特性

- **一键静默换壁纸**：点击应用图标即换壁纸，无弹窗、无通知、无界面跳转
- **缓存预取（秒换）**：后台自动预取 2~5 张壁纸，换壁纸时从缓存读取，不再等待网络；取旧补新（FIFO 队列）
- **图片 API 自定义**：任意直接返回图片字节流的接口，主屏 / 锁屏 / 主屏+锁屏三种模式
- **定时切换**：30 分钟 ~ 24 小时可调，AlarmManager 精确唤醒，到点自动换壁纸
- **壁纸记录**：记录每次换壁纸结果（成功/失败 + 应用成功的壁纸缩略图），双 Tab 展示历史与缓存列表，保留条数可配（10/30/50/100）
- **外链一键换壁纸**：在浏览器打开指定链接即可静默换壁纸（本机安装本应用后有效）
- **缓存目录可自定义**：默认存于 `Android/data/<包名>/files/wallpapers`，可通过系统文件选择器（SAF）换任意目录；壁纸保存为 `.jpg` / `.png`，文件管理器可直接查看
- **动态取色（莫奈）**：Android 12+ 自动跟随系统壁纸取色，深浅色模式自适应
- **静默设计**：换壁纸全程无通知、无 Toast，结果仅写入壁纸记录

## 使用方式

> 使用前请先在设置页填写**图片 API 地址**（返回图片字节流的直链，如 `https://t.alcy.cc/pc/`）。

| 操作 | 效果 |
| --- | --- |
| 点击应用图标 | 静默换壁纸 |
| 长按图标 → 设置 | 进入设置页 |
| 浏览器打开外链 | 静默换壁纸 |
| 设置页开启定时 | 到点自动换壁纸 |

## 界面分区（底部导航）

- **换壁纸**：立即换壁纸、图片 API 地址、壁纸模式、外链复制
- **缓存**：缓存状态、预取数量（2~5）、手动补充、缓存目录
- **定时**：开关、切换间隔、后台保护引导（电池优化白名单 / 自启动）
- **记录**：保留条数、查看壁纸记录

## 技术栈

- Kotlin + Jetpack Compose（Material 3）
- OkHttp（图片下载，指数退避重试）
- Storage Access Framework（SAF `DocumentFile`，自定义缓存目录）
- AlarmManager（定时切换，`setExactAndAllowWhileIdle`）
- SharedPreferences（配置与壁纸记录持久化）
- 莫奈动态取色（`dynamicLightColorScheme` / `dynamicDarkColorScheme`）

## 构建

需要 Android SDK + JDK 17。

```bash
# 命令行构建（Debug）
gradle assembleDebug

# 或直接用 Android Studio 打开项目运行
```

产物路径：`app/build/outputs/apk/debug/app-debug.apk`

## 目录结构

```
app/src/main/java/com/example/wallpaper/
├── MainActivity.kt          # 透明跳板 Activity（点击图标静默换壁纸）
├── WallpaperApp.kt          # Application（启动预取缓存、创建设置磁贴）
├── data/                    # 数据层：网络、配置持久化、缓存队列
│   ├── NetworkModule.kt     # OkHttp 单例
│   ├── PreferenceStore.kt   # SharedPreferences 配置 + 壁纸记录
│   ├── WallpaperCache.kt    # FIFO 缓存队列（File / SAF 双后端）
│   └── WallpaperRepository.kt
├── domain/                  # 领域层：换壁纸统一入口
│   ├── WallpaperChanger.kt  # 缓存优先 → 网络兜底
│   ├── WallpaperSetter.kt   # WallpaperManager 设置
│   └── WallpaperTarget.kt
├── schedule/                # 定时调度
├── shortcut/                # 桌面磁贴 / 外链
└── ui/                      # 设置页 + 壁纸记录页（Compose）
```

## 开源协议

[MIT](LICENSE)

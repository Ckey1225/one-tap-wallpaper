# Wallpaper — 一键静默换壁纸

一个轻量的 Android 原生壁纸应用：**点击应用图标直接静默换壁纸**，全程零界面、零打扰；长按图标可进入设置页。

使用 Kotlin + Jetpack Compose (Material 3) 编写，支持缓存预取、定时切换、外链一键换壁纸与壁纸记录。

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

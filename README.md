# PictureViewer

一个 Android 漫画阅读器应用，支持漫画浏览、阅读和文件管理。

## 功能特性

### 漫画库
- 浏览本地漫画文件夹
- 封面预览
- 长按菜单：刷新、改名、删除

### 阅读器
- 三种阅读模式：垂直滚动、水平翻页、网格视图
- 阅读进度自动保存
- 手势缩放图片

### 文件重命名
- 批量重命名文件
- 数字填充宽度设置（补零）
- 前缀/后缀添加
- 查找/替换功能
- 冲突检测
- 撤销重命名

### 存储访问
- 支持 SAF (Storage Access Framework)
- 访问外部存储文件
- 子文件夹扫描

## 系统要求

- Android 7.0 (API 24) 及以上
- 存储权限

## 下载

从 [Releases](https://github.com/rusting11/PictureViewer/releases) 页面下载 APK 安装。

## 技术栈

- Kotlin
- Jetpack Compose
- Material 3
- Android SAF (Storage Access Framework)
- Coroutines

## 项目结构

```
app/src/main/java/com/example/pictureviewer/
├── MainActivity.kt              # 主活动和导航
├── data/
│   ├── DataStore.kt             # 数据存储
│   ├── LibraryEntry.kt          # 漫画库条目
│   └── model/                   # 数据模型
├── ui/
│   ├── library/                 # 漫画库界面
│   ├── reader/                  # 阅读器界面
│   ├── rename/                  # 重命名界面
│   ├── components/              # 通用组件
│   └── theme/                   # 主题配置
└── util/
    └── FolderScanner.kt         # 文件夹扫描工具
```

## 许可证

MIT License

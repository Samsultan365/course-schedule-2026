# 课程表 · Course Schedule 2026

> 一个面向个人的课程表网页应用：在线看清每周安排，离线也能打开，还能把课程提醒推送到安卓手机。

🔗 **在线访问**：https://samsultan365.github.io/course-schedule-2026/

---

## 功能特色

- 📅 **按天展示课程**：课程、晚自习、调休补课、假期用不同颜色区分，一眼看清。
- 📍 **定位今天**：打开自动高亮当天安排，顶部显示“下一项”课程。
- 🟢 **离线可用（PWA）**：基于 Service Worker 缓存，断网也能查看，支持“添加到主屏幕”像 App 一样使用。
- 🔔 **安卓课程提醒**：配套安卓应用会在上课前 **30 分钟**、晚自习前 **15 分钟**推送系统通知，支持“稍后 10 分钟”。
- 🔄 **自动更新**：安卓应用自动检查新版本并引导下载安装。
- 🚌 **附加信息**：内置国庆/作息调整通知、冬春季校车时刻、节次时间对照。

## 安装方式

**手机浏览器（iPhone / Android）**
1. 用手机自带浏览器打开上面的网址（请勿使用微信、QQ 等内置浏览器）。
2. 点击浏览器菜单 → “安装应用”或“添加到主屏幕”。
3. 确认名称“课程表”即可。

**安卓应用（APK）**
- 在仓库 [Releases](https://github.com/Samsultan365/course-schedule-2026/releases) 中下载最新 `android-v*` 版本 APK 安装。

## 目录结构

```
.
├─ index.html            # 网页主页面（内联课表数据 + 渲染 + 离线注册）
├─ schedule.json         # 结构化课表数据（供安卓端抓取排提醒）
├─ service-worker.js     # 离线缓存策略
├─ manifest.webmanifest  # PWA 清单
├─ course-schedule-*.ics # iPhone / 日历订阅文件
├─ icons/                # 应用图标
├─ .github/workflows/    # 安卓 APK 自动构建工作流
└─ android-app/          # 安卓 WebView 壳应用（提醒 + 版本更新）
```

## 课表数据格式（`schedule.json`）

```json
{
  "version": 3,
  "timezone": "Asia/Shanghai",
  "updated_at": "2026-10-07T15:00:00+08:00",
  "events": [
    {
      "date": "2026-10-08",
      "start": "08:00",
      "end": "09:40",
      "title": "大学英语（一）",
      "location": "宝教一106",
      "kind": "class",
      "reminder_minutes": 30,
      "status": "confirmed",
      "note": ""
    }
  ]
}
```

| 字段 | 说明 |
| --- | --- |
| `date` | 日期，`YYYY-MM-DD` |
| `start` / `end` | 开始/结束时间，`HH:mm` |
| `title` | 课程名称 |
| `location` | 上课地点 |
| `kind` | `class` 课程 / `study` 晚自习 |
| `reminder_minutes` | 提前提醒分钟数（课程 30、晚自习 15） |
| `status` | `confirmed` 已确认 / `tentative` 待定 |
| `note` | 备注，如调休、周次 |

## 更新流程

改动课表时，需要同步以下文件：

1. `index.html` —— 网页内联的 `schedule[]` 数据与页面文案
2. `schedule.json` —— 安卓提醒使用的结构化数据
3. `course-schedule-*.ics` —— iPhone 日历订阅
4. `service-worker.js` + `index.html` 的 `APP_VERSION` —— 升级缓存版本号
5. `android-app/app/src/main/assets/www/` —— 同步安卓内置离线页
6. 如需发布新 APK：更新 `android-app/app/build.gradle` 版本号并打 `android-v*` 标签

> ⚠️ 改完数据记得升级 `CACHE_NAME` / `APP_VERSION`，否则已安装的用户会停留在旧缓存。

## 技术栈

- 纯静态 HTML / CSS / JavaScript（无框架、无构建步骤）
- PWA：Service Worker + Web App Manifest
- 安卓端：Java + WebView + AlarmManager 本地通知
- CI：GitHub Actions 自动构建并签名 APK

---

*本仓库为个人课程安排记录，支持离线查看与上课提醒。*

# TvBox-NY · NOYA Edition

> 基于 [TVBoxOSC](https://github.com/o0tvbox0o/TVBoxOSC) 深度定制的电视观影客户端。
> 一句话：**装上就能看、看着舒服、遥控能按。**
>
> 作者：漩涡鸣人 · QQ 723167066

---

## ✨ 这版和原版的差别

| 维度 | 原版 TVBox | TvBox-NY |
|---|---|---|
| 界面 | 模板直出，白描边+灰底 | NOYA 影院风：近黑底 + 琥珀主色，全站统一画报语言 |
| 海报 | 选中后整体压暗 | 原彩直出，焦点用白环 + 1.03 缩放表达 |
| 数据源选择 | 白色文字长列表 | Netflix 风品牌色磁贴（8 色渐变 + 选中白环） |
| 网页遥控器 | 按键大多无反应 | 自建焦点引擎，全按键可用，带回执显示 |
| 搜索页 | 按钮等宽堆叠、三栏参差 | 主次分明 + 三栏顶部基线对齐 + 键盘降噪 |
| 壁纸 | 单源，挂了就挂了 | 9 类随机壁纸 + 多源兜底链 + 真解码校验 |
| 定时任务 | 普通定时 | NTP 毫秒级卡点（阿里云 ntp.aliyun.com） |

详细升级说明（含代码对照）见 **[COMPARISON.md](COMPARISON.md)** 或网页版 **[COMPARISON.html](COMPARISON.html)**。

## 📱 核心功能

- **网页遥控器 2.0**：手机浏览器打开即控电视。方向/OK/返回/主页/菜单/播放全键可用，
  摇杆带死区 + 三档连发 + 震感，每个按键有发送回执，问题看得见。
- **NTP 毫秒级卡点**：同步阿里云 `ntp.aliyun.com`，定时任务精准到毫秒，卡点场景不差半拍。
- **省份并发请求**：多省份/多站点并发提速，接口拉取更快；配合集中日志回显，一眼定位问题。
- **多源壁纸引擎**：风景 / 二次元 / 动漫 / AI 绘画等 9 类随机壁纸，主源失败自动沿樱花、paugram
  换源轮换，下载后真解码校验，永不"点了没反应"。
- **使用帮助页**：常见问题、配置说明内置在 App 里，不用翻帖子找教程。

## 🚀 体验优化

- 搜索页视觉重排：搜索主键琥珀实心、次键 ghost 降级，节标题统一，键盘按键收敛降噪
- 观看历史 / 收藏 / 搜索历史持久化稳定，重启不丢
- 搜索结果支持缩略图模式，看图找片更快
- 千集长剧选集焦点滚动修复，不再卡死
- DoH 加密 DNS（腾讯 / 阿里 / 360）可选，解析更稳
- 播放器类型可选：系统 / IJK / EXO

## 🌐 内置源接口

App 启动后自带**本机接口服务（端口 9978）**，不用自己搭服务器：

| 地址 | 用途 |
|---|---|
| `http://<电视IP>:9978/` | **内置配置源**：其他设备（手机/另一台盒子）的 TVBox 配置地址直接填这条，即可把这台电视当接口源用，扫码/手输都行 |
| `http://<电视IP>:9978/remote.html` | 网页遥控器：手机浏览器打开即控电视 |

- 手机端「扫码即用」：配置弹窗自带二维码，手机扫一下就把接口填进手机上的 TVBox
- 接口、遥控、日志回显全走这一条通道，部署零成本

## 📦 下载与安装

- 包规格：`java64` · ≈25MB · Android TV 5.0+
- 电视/盒子/投屏器直接安装 APK 即可
- 遥控器：手机浏览器访问 `http://<电视IP>:9978/remote.html`

## 🔧 编译

```bash
./gradlew.bat :app:assembleJava64Debug
# 产物: app/build/outputs/apk/java64/debug/TVBox_debug-java64.apk
```

## ⚙️ 接口配置说明（json 配置示例）

```
//searchable:搜索开关	        0:关闭 1:启用
//filterable:首页可选	        0:否 1:是
//playerType:播放器类型	        0:系统 1:IJK 2:EXO
//采集接口类型		        0:xml 1:json 3:jar 4:remote
//parses解析类型		        0:嗅探,自带播放器 1:解析,返回直链
//直播参数说明                   ua:用户自定义ua epg:节目网址 logo:台标网址
```

```json
{
    "spider": "./your.jar",
    "wallpaper": "./api/img",
    "sites": [],
    "parses": [],
    "hosts": [
        "cache.ott.ystenlive.itv.cmvideo.cn=base-v4-free-mghy.e.cdn.chinamobile.com",
        "cache.ott.bestlive.itv.cmvideo.cn=ip"
    ],
    "lives": [],
    "rules": [],
    "doh": [
        { "name": "腾讯", "url": "https://doh.pub/dns-query" },
        { "name": "阿里", "url": "https://dns.alidns.com/dns-query" },
        { "name": "360",  "url": "https://doh.360.cn/dns-query" }
    ]
}
```

## 📄 声明

本项目基于开源 TVBoxOSC 二次开发，仅供技术研究与学习使用，请于 24 小时内自行删除。
所有影视内容均来自网络公开接口，与本项目无关。

---

**漩涡鸣人** · QQ 723167066

# NeFobric @VERSION@（Minecraft @MC@）

NeFobric 是把 Fabric、Forge、NeoForge 三家模组加载器统一起来的加载器。这是第一个 Alpha 版：它能启动 Minecraft @MC@ 客户端和专用服务端，在同一个 `mods` 文件夹里识别三家的模组，并调用它们的入口。

**这一版还不能运行真正的模组。** Fabric API、Forge 的事件总线和注册表、Mixin 都还没有实现，所以目前只有“只打日志”级别的最简单模组能工作。请先用 `test-mods` 里的三个测试模组验证。

## 包里有什么

| 文件 | 用途 |
| --- | --- |
| `nefobric-loader-@VERSION@.jar` | 加载器本体，客户端和服务端共用 |
| `@VERSION_ID@.json` | 启动器版本文件，继承原版 @MC@ |
| `install-client.bat` / `install-client.ps1` | Windows 客户端安装脚本 |
| `install-client.sh` | Linux / macOS 客户端安装脚本 |
| `start-server.bat` / `start-server.sh` | 专用服务端启动脚本 |
| `test-mods/` | Fabric、Forge、NeoForge 各一个测试模组 |

## 客户端

1. 先在启动器里安装并运行一次原版 @MC@。
2. Windows 双击 `install-client.bat`。`.minecraft` 不在默认位置（`%APPDATA%\.minecraft`）时，在 PowerShell 里运行
   `.\install-client.ps1 -MinecraftDir "你的 .minecraft 路径"`。
3. 官方启动器：安装 → 新安装 → 版本选 “release @VERSION_ID@”。HMCL / PCL 刷新版本列表即可看到 `@VERSION_ID@`。
4. 把 `test-mods` 里的三个 jar 放进 `.minecraft/mods`，启动游戏。

手动安装：把加载器 jar 放到 `.minecraft/libraries/dev/nefobric/nefobric-loader/@VERSION@/`，把版本文件放到 `.minecraft/versions/@VERSION_ID@/`（同时放一个同名的空 `.jar` 文件）。

## 专用服务端

1. 新建文件夹，放入原版 @MC@ 的 `server.jar`、`nefobric-loader-@VERSION@.jar` 和 `start-server.bat`（或 `.sh`）。
2. 模组放进同一文件夹下的 `mods`。
3. 运行 `start-server.bat`，或执行 `java -jar nefobric-loader-@VERSION@.jar nogui`。

第一次启动会像原版一样把服务端文件解到 `versions/` 和 `libraries/`。

## 怎么判断成功

日志（控制台，以及 `logs/nefobric-latest.log`）里应该依次出现：

```
NeFobric @VERSION@ 正在启动 Minecraft @MC@（客户端）
正在加载 3 个模组（Fabric 1, Forge 1, NeoForge 1）：
[fabric_hello] preLaunch
已在 net.minecraft.client.main.Main 中插入游戏钩子
[forge_hello] constructed: Hello from Forge!
[neoforge_hello] constructed: Hello from NeoForge!
[fabric_hello] main: Hello from Fabric! ... thread=Render thread ... gameLoader=NeFobric
[fabric_hello] client: client entrypoint ran
3 个模组初始化完成
```

之后游戏应当正常进入标题界面（服务端则正常开服）。如果出现“没有找到预期的代码位置”的警告，说明游戏钩子没对上原版代码，请把日志发回来。

## 设置项

| JVM 参数 | 作用 |
| --- | --- |
| `-Dnefobric.lang=zh` 或 `en` | 强制界面语言 |
| `-Dnefobric.debug=true` | 输出调试日志 |
| `-Dnefobric.modsDir=路径` | 换一个模组文件夹 |
| `-Dnefobric.addMods=a.jar;b.jar` | 额外加载的模组（分隔符同 classpath） |
| `-Dnefobric.gameJar=路径` | 手动指定游戏 jar（客户端）或 server.jar（服务端） |

## 出了问题

NeFobric 会在控制台打出带分隔线的说明，并写入 `crash-reports/nefobric-*.txt`。请把这个文件和 `logs/nefobric-latest.log` 发给 NeFobric，而不是发给模组作者。

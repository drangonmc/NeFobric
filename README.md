# NeFobric

NeFobric 是一个从零自研的 Minecraft 模组加载器，目标是让 Fabric、Forge、NeoForge 三家的模组放进同一个 `mods` 文件夹一起运行。首个目标版本是 Minecraft 1.20.1，客户端和专用服务端都支持。

当前版本：**0.0.1-alpha**（最小可运行骨架）。设计依据见架构文档《NeFobric 统一架构设计（v0.1）》。

## V0.0.1 Alpha 范围

| 已完成 | 说明 |
| --- | --- |
| 两个启动入口 | 客户端：启动器版本文件继承原版 1.20.1，主类为 `dev.nefobric.loader.launch.ClientMain`。专用服务端：`java -jar nefobric-loader.jar nogui`，读取原版 `server.jar`（bundler 格式）并解出服务端和库 |
| 自研类加载器 | `NeFobricClassLoader`：游戏和所有模组共用的平铺类加载器，子优先，类在定义前经过变换流水线 |
| 模组发现 | 扫描 `mods/`，同时识别 `fabric.mod.json` 和 `META-INF/mods.toml`，按依赖区分 Forge 与 NeoForge；跳过 Quilt、1.20.5+ NeoForge、非模组 jar 并说明原因 |
| 统一元数据 | `ModCandidate`：id、版本、生态、入口、依赖、内嵌 jar、Mixin 配置、访问变换 |
| 依赖解析 | Fabric（SemVer 条件）与 Forge（Maven 区间）各按自己的语义比较版本；缺依赖、版本不符、重复 id、不兼容都给出中英文可读报错并写崩溃报告；依赖先于被依赖者 |
| 游戏钩子 | `GameEntryPatch` 用 ASM 在原版主类里插入一次调用，只靠字符串常量定位，不需要映射表 |
| 入口调用 | Fabric：`preLaunch`、`main`、`client`、`server`（支持 `Class`、`Class::field`、`Class::method` 三种写法）。Forge 系：扫描 `@Mod` 并用无参构造创建主类 |
| 最小 API | `net.fabricmc.api.*` 入口接口、`FabricLoader`（常用方法）、`net.minecraftforge.fml.common.Mod` |
| 测试 | 单元测试 + 端到端测试（模拟 1.20.1 启动流程的假游戏 + 三个测试模组） |

0.0.1-alpha 之后已经合并到 main（下一个版本发布）：

| 已完成 | 说明 |
| --- | --- |
| 重映射 | 原版按 Mojang 官方映射和 MCPConfig 重映射成“Mojang 类名 + SRG 成员名”（和 Forge 1.20.1 生产环境一致）；Fabric 模组从 intermediary 整体重映射（含 refmap、Mixin 注解里的字符串）。映射文件首次启动时下载，结果按输入缓存 |
| 类变换流水线 | `TransformerPhase`：`GAME_PATCH` → `STRIP` → `ACCESS` → `MIXIN`，兼容层通过 `EcosystemAdapter.registerTransformers` 挂变换器 |
| Mixin | 加载器自带 Fabric 的 Mixin 分支 0.15.4（上游 0.8.7）和 MixinExtras 0.4.1，Fabric 与 Forge 系模组共用。按模组加载顺序注册配置，Fabric 配置带模组 id 和兼容等级装饰；Mixin 读类信息时看到的是 Mixin 之前各阶段的结果 |
| Fabric Loader API | 完整的 Fabric Loader 0.16 公开 API、`fabric.mod.json` 全字段、access widener、`@Environment` 剥离 |
| Jar-in-Jar | Fabric 的 `META-INF/jars` 和 Forge 的 `META-INF/jarjar` 按内容解到 `.nefobric/cache/jij/`，可以多层嵌套。内嵌的模组和普通模组一样参与依赖解析：同一个模组被多处内嵌时取最高版本，内嵌它的模组没被选中（或不在这一端运行）时它也不加载；没有模组元数据的内嵌 jar 作为普通库加载；内嵌的 MixinExtras 等加载器自带的模组让给自带的那份 |

**还没有做**：Forge 的 access transformer、Fabric API、Forge 的事件总线 / 注册表 / 配置 / 网络、Forge 补丁重现、图形安装器、错误弹窗。

## 加载流程

1. **找游戏**：客户端在 classpath 上找含 `net/minecraft/client/main/Main.class` 的 jar；服务端读 `server.jar`，按 `META-INF/versions.list` / `libraries.list` 解到 `versions/`、`libraries/`（校验 sha256，已存在则跳过）。从 jar 里的 `version.json` 读版本号。
2. **发现模组**：每个兼容层（通过 `ServiceLoader` 注册的 `EcosystemAdapter`）读自己认识的元数据。同一个 jar 同时带两份元数据时优先 Fabric 版。模组内嵌的 jar 解到缓存里，用同样的方式读。
3. **解析依赖**：加上内置虚拟模组（`minecraft`、`java`、`nefobric`、`mixinextras`、`fabricloader`、`forge`、`neoforge`、`javafml`、`lowcodefml`），检查依赖并排出加载顺序。
4. **类加载器**：游戏 jar + 兼容层运行库 + 模组 jar + 选中模组内嵌的普通库交给 `NeFobricClassLoader`；装上游戏钩子变换器，再让各兼容层挂上自己的变换器。
5. **Mixin**：注册各模组的 Mixin 配置，进入 DEFAULT 阶段，先触发一次配置选择（Mixin 插件的 `onLoad` 在这里运行），然后把 Mixin 挂到流水线最后。配置有错时在移交游戏之前报出来。
6. **preLaunch**：Fabric `preLaunch` 入口。
7. **移交原版主类**。原版运行到钩子位置时：
   - 客户端：和 Fabric Loader 相同，在游戏对象 `Minecraft` 的构造函数里、游戏目录赋值之后（渲染线程已初始化，`Minecraft.getInstance()` 可用；游戏对象同时交给 `NeFobricLoader.gameInstance()`）。模组初始化失败时原版能照常写出 “Initializing game” 崩溃报告。找不到插入点时退回到主类 `setName("Render thread")` 之后；
   - 服务端：读取 `server.properties` 之前（Bootstrap 之后，与 Fabric Loader 的位置相同）。模组初始化失败时以退出码 1 结束（原版这里会以 0 退出）。
8. **钩子触发**：构造 Forge 系 `@Mod` 主类 → Fabric `main` → 按端运行 Fabric `client` 或 `server`。这与架构文档“生命周期对齐”一节的顺序一致。

内置虚拟模组的版本号（`fabricloader` 0.16.10、`forge` 47.4.0、`neoforge` 47.1.106）只用于通过模组的依赖检查，并不代表已经实现了对应版本的 API。依赖 `fabric-api` 的模组会因为“未安装 fabric-api”而停下，这是有意的：Alpha 还不提供 Fabric API。

## 项目结构

```
loader/          核心：启动、类加载、发现、解析、游戏钩子（dev.nefobric.loader）
compat-fabric/   Fabric 兼容层 + Fabric Loader 公开 API（net.fabricmc.*）
compat-forge/    Forge 系兼容层 + @Mod 注解（net.minecraftforge.fml.common.Mod）
dist/            打包成单一 jar（依赖库重定位到 dev.nefobric.shadow）和发布包
testgame/        模拟 1.20.1 启动流程的假游戏（客户端 jar、bundler 格式的 server.jar）
testmods/        fabric-hello、forge-hello、neoforge-hello 三个测试模组
integration-test/ 端到端测试：真实子进程启动假游戏并加载三个测试模组
```

## 构建

需要 JDK 17 或更高版本（产物以 Java 17 为目标）。

```
./gradlew build                 # 编译 + 全部测试
./gradlew :dist:packageRelease  # 生成 dist/build/distributions/nefobric-0.0.1-alpha.zip
```

发布包的安装方法见包内 `README.md`。

## 真机验证与已知风险

2026-11-22 在 Windows 10 x64、OpenJDK 17.0.20.1 上用真实的原版 1.20.1 测过（直接用 `java -cp` 启动客户端，用 `java -jar` 启动专用服务端，带三个测试模组）：

- 两个游戏钩子都插入成功，没有出现“没有找到预期的代码位置”的警告。客户端模组在 `Render thread` 上、构造 `Minecraft` 对象之前初始化；服务端在 `main` 线程上、读取 `server.properties` 之前初始化。
- 三个测试模组的入口都按顺序运行，客户端进入标题界面，服务端解出 bundler 里的 30 个文件后运行到原版的 EULA 检查。
- 日志里除了原版本来就有的警告（假令牌导致的 401、缺少山羊角音效、着色器 Sampler2、首次开服没有 `server.properties`）之外没有错误。

还没验证的：

- 通过安装脚本装进官方启动器、HMCL、PCL 后启动（这次没有经过启动器）。它们对只有 `name` 的 library 条目的处理方式需要实测。
- 加载器与模组共享 API 类的方式（`net.fabricmc.api.*` 等由加载器提供）在模组自带同名类时可能冲突。

## 许可证

尚未确定，见架构文档“风险、许可证与开放问题”。

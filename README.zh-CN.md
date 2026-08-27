# Minecraft AI 玩伴

[English](README.md)

这是一个面向自主管理 Minecraft 局域网世界的陪伴型机器人项目。

**首个目标版本：Minecraft Java Edition 1.20.1。**

项目目标不是自主速通，而是让机器人更像一名合作玩家：陪在玩家身边、响应私聊指令、完成有限协助任务、报告危险，并在玩家要求时立即停止。

## 架构决策

玩伴将在一个独立的 Minecraft Java 客户端中运行。首个基线是 Minecraft Java Edition 1.20.1、Fabric，以及内置兼容 Baritone 的 MiranCZ AltoClef 分支。Minecraft 1.20.1 的运行环境为 Java 17；当前多版本客户端源码的构建环境需要 Java 21。

```text
玩家的 Minecraft 客户端                 玩伴的 Minecraft 客户端
        |                                          |
        +--------------- 局域网世界 ----------------+
                                                   |
                                  Companion Mod
                                  - 陪伴行为和权限控制
                                  - 私聊命令适配器
                                  - 安全控制器和记忆
                                  - AltoClef 任务与 Baritone 移动
                                  - 可选 LLM 规划器
```

这会需要第二个 Minecraft 客户端实例，但能让玩伴拥有与玩家相同的客户端世界模型和交互能力。BaritonePlus 的公开代码维护活跃度较低，因此不作为运行时依赖。

## 当前行动层

白名单玩家能够通过私聊命令让机器人：

- `collect <item> <count>`：取得目录中可获取的资源，底层自动处理工具、采集、合成和熔炼链；
- `craft <item> <count>`、`smelt <item> <count>`：通过同一资源链准备材料和工作站；
- `goto <x> <y> <z>`、`follow`、`come`、`home`：移动和陪伴；
- `attack <entity> <count>`、`protect`、`unprotect`：攻击指定实体或保护主人附近的敌对生物；
- `give <item> <count>`：只交付机器人当前背包中已有的物品；
- `status`、`queue`、`stop`：查询、排队和紧急中断。

数量必须在 `1` 到 `64` 之间。每条私聊只接受一个结构化命令；无效物品、实体、参数或未知动词会在启动任务前被拒绝。调度器按自救、保护、明确移动、其他任务的顺序运行，同级保持发送顺序。`stop` 立即取消并清空队列。

每一条命令都必须依据游戏状态返回已验证的成功、失败或取消结果。机器人不得在未检查游戏状态时宣称任务成功。

## 可选远程 LLM

`ai` 私聊入口从伙伴实例游戏目录下的 `agent/llm.properties` 读取 OpenAI 兼容接口配置，不需要 JVM 参数。文件格式如下，请只在本机填写真实密钥，不要提交到 Git：

```properties
url=https://example.invalid/v1
model=your-model-id
key=your-api-key
```

客户端会请求 `{url}/chat/completions`，既支持填写服务器根地址，也支持已经以 `/v1` 结尾的地址。测试 Minecraft 前，应先确认 `{url}/models` 能列出模型，并且聊天请求返回 HTTP 200。若接口返回 HTTP 503，表示网关在线但模型后端不可用，伙伴不会产生 AI 工具动作。

## 约束

- 初始验证只在自主管理的 Minecraft Java 1.20.1 局域网世界中进行。
- 玩伴以被世界接受的独立玩家身份连接。
- 不包含认证绕过、自动注册或登录、反作弊规避，也不会无约束地在公共聊天中发言。
- 主人可授权任务使用 AltoClef 已有的挖掘、放置、战斗与跨维度能力；玩家在设置中标记为受保护的位置仍不会被破坏或放置。
- `protect` 只攻击主人附近的敌对生物，不自动攻击其他玩家。

## 路线图

1. 完成 PCL 双客户端实机验证，包括危险插队、任务恢复和失败回报。
2. 为 `build` 增加蓝图/区域参数，不接受模糊的自然语言建造请求。
3. 增加短期记忆、人格，以及只能选择已批准高层意图的 LLM。

完整的验收场景和进度记录见 [docs/roadmap.md](docs/roadmap.md)。
本机构建和首次运行的安全配置见 [docs/getting-started.md](docs/getting-started.md)。

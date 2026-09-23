# Unity + Spring Boot + Python Agent 最小联调示例

这个示例展示三层如何交互，不是完整医疗训练产品。原始毕业设计要求见 [readme.md](readme.md)。

macOS 打包、Docker 和临时线上部署见 [DEPLOYMENT.md](DEPLOYMENT.md)。

```
Unity C# (8080)
  POST /api/sessions                    -> Spring Boot 创建会话
  POST /api/sessions/{id}/events/advance -> Spring Boot 更新阶段
  POST /api/sessions/{id}/messages       -> Spring Boot 读取阶段和最近对话历史
                                         -> Python Agent 服务 (8081) /internal/respond
                                         -> 可选 OpenAI Responses API
                                         <- Agent 回答，或无 key 时 HTTP 504 超时结果
                                         <- Spring Boot 原样传回 Unity
                                         <- Unity 显示回答或超时反馈
```

## 准备

- Java 17+ 与 Python 3.10+。项目包含 Maven Wrapper，不必另外安装 Maven；首次运行会下载 Maven、Java 与 Python 依赖。
- `UnityProject` 包含 Unity 6 项目配置；也可以只把 `VrAgentDemoClient.cs` 复制到已有项目。示例场景先在桌面 Game 视图联调，不依赖 XR 包。
- 无须 API key 也能验证三层链路：Agent 服务会返回模拟超时（HTTP 504），Unity 会显示超时反馈。没有 key 时不会生成固定医疗回答或环境建议。

首次使用，在项目根目录使用 Python 3.10+ 创建虚拟环境并安装依赖：

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r agent-python\requirements.txt
```

终端一先启动 Python Agent：

```powershell
.\.venv\Scripts\python.exe -m uvicorn app.main:app --app-dir agent-python --host 127.0.0.1 --port 8081
```

终端二启动 Java 主服务：

```powershell
.\mvnw.cmd '-Dmaven.repo.local=.m2cache\repository' -pl backend spring-boot:run
```

Python Agent 监听 `127.0.0.1:8081`；Java 主服务监听 `8080`。原来的 Java Agent 代码仍在 `agent-service/`，仅作对照；不要同时启动两个 Agent 服务占用 `8081`。

## 多轮对话如何工作

Java 主服务按 `sessionId` 在内存中保存成功的对话。每次提问时，它把当前阶段、回答长度偏好和最近最多 20 条消息发给 Python Agent；Python 将这些消息作为 Responses API 的输入。只有模型成功返回的用户/Agent消息对才会写入历史，超时和错误不会污染上下文。重新启动 Java 会丢失当前历史，这是 baseline 阶段的预期行为。

可以用下面两个接口检查或清空某个会话：

```http
GET    /api/sessions/{id}/messages
DELETE /api/sessions/{id}/messages
```

最简单的多轮验证是先说 `My name is Alex.`，再问 `What is my name?`。在同一个 session 中应回答 Alex；新建 session 后则不应知道这个名字。当前版本尚未接入 RAG，因此 Agent 只有系统指令、训练阶段说明和本次会话历史，没有课程文档知识库。

## 从 Unity 进入并看到反馈

在 Unity Hub 的 **Projects > Open** 中选择仓库根目录下的 `UnityProject` 文件夹本身。此项目使用 Unity `6000.6.0f1`。如果 Hub 没有识别到该编辑器，可先在 Hub 的 **Installs > Locate** 中选择对应版本的 `Unity.exe`，再打开项目。

1. 在 Unity 中打开 `UnityProject`，等待脚本导入完成，然后在 Project 面板双击 `Assets/Scenes/Demo.unity`。如果当前是空白的 `Untitled` 场景，双击后才会切换到演示场景。它包含训练房间、病床、患者与 Agent 占位角色、摄像机和灯光。也可以使用 `Tools > VR Agent > Open Demo Scene`。若正在 Play 模式，先退出 Play 模式再切换场景。
2. 进入 Play 模式。Game 视图左上方有演示面板，输入问题并点击 `Ask Agent`。Unity 发送请求到 Java 主服务，主服务转发给 Agent 服务，再把结果传回 Unity。
3. **不填 API key 时**，面板应显示 `Agent timed out in demo mode: no API key is configured. [timeout]`，且没有环境建议。点 `Advance stage` 仍能看到 Java 维护的场景阶段变化；再次提问仍会得到超时反馈。
4. **填入有效 key 与模型 ID 后**，Agent 才会返回真实模型回答。若提问涉及背景声音，Unity 会显示降噪建议；点击 `Apply suggestion` 后才会改变设置。若给脚本的 `Background Audio` 槽位连接了场景背景音，音量会降至 25%。

这个面板仅用于桌面联调。真正的 VR 版本应把相同的三个公开方法接到世界空间 UI 或手柄交互上，并将 Capsule 替换成角色模型。

如果在独立 VR 头显上运行，`localhost` 指的是头显本身。把 Inspector 中 `Backend Base Url` 改为运行 Spring 的电脑在同一网络中的地址，例如 `http://192.168.x.x:8080`，并确保本机网络与防火墙允许该连接。API key 始终留在 Agent 服务电脑上。

## 可选：不打开 Unity 时检查 Java 收发

在第三个 PowerShell 终端执行：

```powershell
$session = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/sessions -ContentType 'application/json' -Body '{}'
$id = $session.sessionId
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/sessions/$id/events/advance" -ContentType 'application/json' -Body '{}'
try {
    Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/sessions/$id/messages" -ContentType 'application/json' -Body '{"question":"What next?","answerLength":"short"}'
} catch {
    $response = $_.Exception.Response
    "HTTP status: $([int]$response.StatusCode)"
    $reader = [System.IO.StreamReader]::new($response.GetResponseStream())
    try { $reader.ReadToEnd() } finally { $reader.Dispose() }
}
```

没有 key 时应显示 HTTP 504 和 `"mode":"timeout"`。这是专门用于联调的模拟超时，收到响应比等待真实网络超时更快；Unity 中仍能确认请求经过 Java 主服务和 Python Agent 并返回。

## 接入真实模型

只在启动 **Python Agent 服务** 的终端设置环境变量，不要把真实 key 写入 `.env.example`、Unity 脚本或版本库：

```powershell
$env:DEEPSEEK_API_KEY = '在这里填入你自己的 DeepSeek API key'
$env:DEEPSEEK_MODEL = 'deepseek-flash'
.\.venv\Scripts\python.exe -m uvicorn app.main:app --app-dir agent-python --host 127.0.0.1 --port 8081
```

Agent 服务读取这两个值。`DEEPSEEK_MODEL` 可省略，代码会默认使用 `deepseek-flash`；也可以按账号权限改为 `deepseek-v4-pro`。没有 key 时返回 HTTP 504 模拟超时；模型调用失败时返回错误而不是伪装成成功。真实请求使用 DeepSeek Responses API。DeepSeek 的 Responses API 是无状态的，因此 Java 会在每次调用时发送本次会话的最近对话历史。如需接入 RAG 或换供应商，只需修改 `agent-python`，Unity 与 Java 主服务的 JSON 契约保持不变。当前还没有 RAG。

## 本示例的边界

- 会话只保存在主服务内存中，重启会丢失；没有用户认证或数据库。
- `AdvanceScenario` 是演示按钮；正式版应由 Unity 中经过验证的任务完成事件触发。
- 没有实现多人同步、语音、真实医疗流程、顾问组评估或生产级安全控制。医疗步骤需要由课程与领域专家审核后才能写入 Agent 的场景知识。
- Agent 的环境建议采用确定性规则，Unity 中由用户主动应用；模型不能直接操作场景。

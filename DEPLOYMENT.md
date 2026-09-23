# macOS 客户端与临时线上服务

## 推荐展示结构

```text
macOS Unity 应用
    -> HTTPS Java Backend（唯一公网入口）
        -> 私有网络 Python Agent
            -> DeepSeek Responses API
```

不要把 `DEEPSEEK_API_KEY` 放进 Unity、仓库或 Java 客户端配置。Python Agent 不需要暴露到公网。

## 1. 配置 Unity 的线上地址

编辑：

```text
UnityProject/Assets/StreamingAssets/backend-url.txt
```

将内容改成部署后 Java 服务的 HTTPS 地址，例如：

```text
https://vr-agent-demo.example.com
```

结尾不需要 `/`。macOS 应用启动时会读取这个文件。调试时也可从终端覆盖：

```bash
open UnityProject/Builds/macOS/VRAgentDemo.app --args --backend-url=https://example.com
```

## 2. 构建 macOS 应用

当前 Windows 上的 Unity `6000.6.0f1` 只有 Windows 与 WebGL 模块，首先在 Unity Hub 的 Installs 中给该版本添加 **Mac Build Support**。如果 Hub 不提供或安装失败，把项目复制到 Mac，在 Mac 上安装同版本 Unity 和 Mac Build Support。

在 Unity 中：

1. 打开 `Assets/Scenes/Demo.unity`。
2. 打开 `File > Build Profiles`，添加并切换到 macOS。
3. 架构选择 `Intel 64-bit + Apple Silicon`，便于同时支持 Intel Mac 和 Apple Silicon Mac。
4. 使用菜单 `Tools > VR Agent > Build macOS Demo`。
5. 产物位于 `UnityProject/Builds/macOS/VRAgentDemo.app`。

只在自己的 Mac 或课堂机器上展示时，可以压缩 `.app` 后传到 Mac，首次打开时用 Finder 右键 **Open**。若要公开分发，需要 Apple Developer ID 签名和 notarization；这一步通常在 Mac/Xcode 或 Unity Build Automation 中完成。

## 3. 本地用 Docker 验证部署形态

在项目根目录创建 `.env`（已被 `.gitignore` 忽略）：

```dotenv
DEEPSEEK_API_KEY=你的真实key
DEEPSEEK_MODEL=deepseek-flash
```

然后执行：

```powershell
docker compose up --build
```

只有 Java 的 `8080` 被发布到宿主机；Python 的 `8081` 只在 Docker 内部网络可见。验证：

```powershell
Invoke-RestMethod http://localhost:8080/health
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/sessions -ContentType 'application/json' -Body '{}'
```

结束展示环境：

```powershell
docker compose down
```

## 4. 临时线上部署

### 方案 A：Render 两个服务

将代码放在私有 Git 仓库，然后创建同一区域的两个服务：

1. Python 创建为 **Private Service**，Dockerfile 选择 `agent-python/Dockerfile`，Docker context 选择 `agent-python`，端口设为 `8081`。
2. 给 Python 服务添加 Secret：`DEEPSEEK_API_KEY`，并设置 `DEEPSEEK_MODEL=deepseek-flash`。
3. Java 创建为公网 **Web Service**，Dockerfile 选择 `backend/Dockerfile`，Docker context 使用仓库根目录，健康检查路径为 `/health`。
4. 在 Java 服务中设置 `AGENT_URL` 为 Python 服务显示的私有地址，例如 `http://vr-agent-python:8081`。以 Render 控制台实际给出的地址为准。
5. Java 服务会得到一个 HTTPS `onrender.com` 地址，把它写进 Unity 的 `backend-url.txt` 后重新构建。

### 方案 B：一台临时 Linux VM

在安装了 Docker 的 VM 上克隆仓库、创建 `.env`，然后运行：

```bash
docker compose up -d --build
```

安全组只开放 Java 的 `8080`，不要开放 `8081`。正式展示最好再用 Caddy、Nginx 或云负载均衡器给 Java 提供 HTTPS 域名，然后把该 HTTPS 地址写入 Unity 配置。

## 展示限制

- Java 会话和聊天历史目前仅在内存中，服务重启或休眠后会丢失。
- 当前没有登录、配额或限流。不要长期公开 URL；展示后关闭服务或撤销/轮换 DeepSeek key。
- 如果托管平台会休眠，第一次请求可能较慢。正式展示前先访问 `/health` 预热。
- macOS 构建和签名必须在目标 Mac 上做最终验证。

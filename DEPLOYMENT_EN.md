# macOS Client and Temporary Online Services

[中文版](DEPLOYMENT.md)

## Recommended demo architecture

```text
macOS Unity application
    -> HTTPS Java Backend (only public entry point)
        -> Python Agent on a private network
            -> DeepSeek Responses API
```

Do not put `DEEPSEEK_API_KEY` in Unity, the repository, or the Java client configuration. The Python Agent does not need to be exposed to the public internet.

## 1. Configure Unity's online service address

Edit:

```text
UnityProject/Assets/StreamingAssets/backend-url.txt
```

Set its content to the deployed Java service's HTTPS address, for example:

```text
https://vr-agent-demo.example.com
```

Do not include a trailing `/`. The macOS application reads this file at startup. For debugging, the value can also be overridden from the terminal:

```bash
open UnityProject/Builds/macOS/VRAgentDemo.app --args --backend-url=https://example.com
```

## 2. Build the macOS application

The current Unity `6000.6.0f1` installation on Windows includes only the Windows and WebGL modules. First, add **Mac Build Support** to this Unity version from Installs in Unity Hub. If Hub does not provide the module or installation fails, copy the project to a Mac and install the same Unity version with Mac Build Support there.

In Unity:

1. Open `Assets/Scenes/Demo.unity`.
2. Open `File > Build Profiles`, add macOS, and switch to it.
3. Select `Intel 64-bit + Apple Silicon` as the architecture to support both Intel Macs and Apple Silicon Macs.
4. Use `Tools > VR Agent > Build macOS Demo`.
5. The output is located at `UnityProject/Builds/macOS/VRAgentDemo.app`.

For demonstrations only on your own Mac or a classroom machine, compress the `.app` and transfer it to the Mac. The first time it is launched, right-click it in Finder and select **Open**. Public distribution requires Apple Developer ID signing and notarization, which are usually completed on a Mac with Xcode or through Unity Build Automation.

## 3. Validate the deployment locally with Docker

Create `.env` in the repository root. It is already ignored by `.gitignore`:

```dotenv
DEEPSEEK_API_KEY=your-real-key
DEEPSEEK_MODEL=deepseek-flash
```

Then run:

```powershell
docker compose up --build
```

Only Java port `8080` is published to the host. Python port `8081` is visible only within the Docker network. Verify the services:

```powershell
Invoke-RestMethod http://localhost:8080/health
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/sessions -ContentType 'application/json' -Body '{}'
```

Stop the demo environment:

```powershell
docker compose down
```

## 4. Temporary online deployment

### Option A: Two Render services

Put the code in a private Git repository, then create two services in the same region:

1. Create Python as a **Private Service**, select `agent-python/Dockerfile`, use `agent-python` as the Docker context, and set the port to `8081`.
2. Add the `DEEPSEEK_API_KEY` secret to the Python service and set `DEEPSEEK_MODEL=deepseek-flash`.
3. Create Java as a public **Web Service**, select `backend/Dockerfile`, use the repository root as the Docker context, and set the health-check path to `/health`.
4. Set `AGENT_URL` in the Java service to the private address shown for the Python service, such as `http://vr-agent-python:8081`. Use the actual address provided by the Render dashboard.
5. The Java service receives an HTTPS `onrender.com` address. Write it to Unity's `backend-url.txt` and rebuild.

### Option B: A temporary Linux VM

On a VM with Docker installed, clone the repository, create `.env`, and run:

```bash
docker compose up -d --build
```

Open only Java port `8080` in the security group; do not open `8081`. For a formal demonstration, use Caddy, Nginx, or a cloud load balancer to provide an HTTPS domain for Java, then write that HTTPS address to the Unity configuration.

## Demo limitations

- Java sessions and chat history are currently stored only in memory and are lost when the service restarts or sleeps.
- Login, quotas, and rate limiting are not currently implemented. Do not expose the URL long term. Shut down the service or revoke/rotate the DeepSeek key after the demonstration.
- If the hosting platform sleeps, the first request may be slow. Visit `/health` before the formal demonstration to warm up the service.
- The macOS build and signing must be validated on the target Mac.

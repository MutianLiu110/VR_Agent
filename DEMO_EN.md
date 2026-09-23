# Minimal Unity + Spring Boot + Python Agent Integration Demo

[中文版](DEMO.md)

This example demonstrates how the three layers interact. It is not a complete medical training product. See [readme.md](readme.md) for the original capstone project requirements.

For macOS builds, Docker, and temporary online deployment, see [DEPLOYMENT_EN.md](DEPLOYMENT_EN.md).

```
Unity C# (8080)
  POST /api/sessions                    -> Spring Boot creates a session
  POST /api/sessions/{id}/events/advance -> Spring Boot advances the stage
  POST /api/sessions/{id}/messages       -> Spring Boot reads the stage and recent conversation history
                                         -> Python Agent service (8081) /internal/respond
                                         -> Optional OpenAI Responses API
                                         <- Agent response, or HTTP 504 timeout result when no key is configured
                                         <- Spring Boot passes the result back to Unity unchanged
                                         <- Unity displays the response or timeout feedback
```

## Prerequisites

- Java 17+ and Python 3.10+. The project includes Maven Wrapper, so Maven does not need to be installed separately. Maven, Java, and Python dependencies will be downloaded on the first run.
- `UnityProject` contains the Unity 6 project configuration. Alternatively, copy only `VrAgentDemoClient.cs` into an existing project. The example scene can first be tested in the desktop Game view and does not depend on XR packages.
- The three-layer connection can be tested without an API key: the Agent service returns a simulated timeout (HTTP 504), and Unity displays timeout feedback. Without a key, no fixed medical responses or environment suggestions are generated.

On first use, create a virtual environment with Python 3.10+ in the repository root and install the dependencies:

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r agent-python\requirements.txt
```

Start the Python Agent in the first terminal:

```powershell
.\.venv\Scripts\python.exe -m uvicorn app.main:app --app-dir agent-python --host 127.0.0.1 --port 8081
```

Start the main Java service in the second terminal:

```powershell
.\mvnw.cmd '-Dmaven.repo.local=.m2cache\repository' -pl backend spring-boot:run
```

The Python Agent listens on `127.0.0.1:8081`, and the main Java service listens on `8080`. The original Java Agent code remains in `agent-service/` for reference only. Do not run both Agent services on `8081` at the same time.

## How multi-turn conversations work

The main Java service stores successful conversations in memory by `sessionId`. For each question, it sends the current stage, response-length preference, and up to 20 of the most recent messages to the Python Agent. Python uses these messages as input to the Responses API. Only user/Agent message pairs successfully returned by the model are written to the history; timeouts and errors do not pollute the context. Restarting Java clears the current history, which is expected during the baseline stage.

Use the following two endpoints to inspect or clear a session:

```http
GET    /api/sessions/{id}/messages
DELETE /api/sessions/{id}/messages
```

The simplest multi-turn test is to say `My name is Alex.` and then ask `What is my name?`. The Agent should answer Alex within the same session, but it should not know the name in a new session. RAG is not yet integrated, so the Agent only has the system instructions, training-stage description, and current session history; it has no course-document knowledge base.

## Open the project in Unity and view feedback

In Unity Hub, select **Projects > Open**, then choose the `UnityProject` folder in the repository root. This project uses Unity `6000.6.0f1`. If Hub does not recognize that editor, select the corresponding `Unity.exe` under **Installs > Locate** before opening the project.

1. Open `UnityProject` in Unity and wait for the scripts to finish importing. In the Project panel, double-click `Assets/Scenes/Demo.unity`. If the current scene is a blank `Untitled` scene, it will not switch to the demo scene until you double-click the file. The scene contains a training room, bed, patient and Agent placeholder characters, a camera, and lights. You can also use `Tools > VR Agent > Open Demo Scene`. Exit Play mode before switching scenes.
2. Enter Play mode. A demo panel appears in the upper-left corner of the Game view. Enter a question and click `Ask Agent`. Unity sends the request to the main Java service, which forwards it to the Agent service and returns the result to Unity.
3. **Without an API key**, the panel should display `Agent timed out in demo mode: no API key is configured. [timeout]`, with no environment suggestion. Clicking `Advance stage` still shows the scenario-stage changes maintained by Java. Asking another question still produces timeout feedback.
4. **With a valid key and model ID**, the Agent returns a real model response. If a question concerns background sound, Unity displays a noise-reduction suggestion. The setting changes only after clicking `Apply suggestion`. If scene background audio is connected to the script's `Background Audio` field, its volume is reduced to 25%.

This panel is intended only for desktop integration testing. A real VR version should connect the same three public methods to world-space UI or controller interactions and replace the Capsule with a character model.

On a standalone VR headset, `localhost` refers to the headset itself. In the Inspector, change `Backend Base Url` to the address of the computer running Spring on the same network, such as `http://192.168.x.x:8080`, and ensure that the local network and firewall allow the connection. The API key must always remain on the computer running the Agent service.

## Optional: test Java requests and responses without opening Unity

Run the following in a third PowerShell terminal:

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

Without a key, the result should show HTTP 504 and `"mode":"timeout"`. This simulated timeout is designed for integration testing and returns faster than waiting for a real network timeout. Unity can still confirm that the request passed through the main Java service and Python Agent and returned.

## Connect a real model

Set the environment variables only in the terminal that starts the **Python Agent service**. Do not write a real key to `.env.example`, Unity scripts, or the repository:

```powershell
$env:DEEPSEEK_API_KEY = 'enter your DeepSeek API key here'
$env:DEEPSEEK_MODEL = 'deepseek-flash'
.\.venv\Scripts\python.exe -m uvicorn app.main:app --app-dir agent-python --host 127.0.0.1 --port 8081
```

The Agent service reads these two values. `DEEPSEEK_MODEL` is optional; the code uses `deepseek-flash` by default. Depending on account access, it can also be changed to `deepseek-v4-pro`. Without a key, the service returns a simulated HTTP 504 timeout. If the model call fails, it returns an error rather than pretending to succeed. Real requests use the DeepSeek Responses API. Because the DeepSeek Responses API is stateless, Java sends the recent history of the current session with every call. To integrate RAG or change providers, only `agent-python` needs to be modified; the JSON contract between Unity and the main Java service remains unchanged. RAG is not currently implemented.

## Scope and limitations

- Sessions are stored only in the main service's memory and are lost after a restart. There is no user authentication or database.
- `AdvanceScenario` is a demo button. In the production version, it should be triggered by validated task-completion events in Unity.
- Multiplayer synchronization, voice, real medical workflows, advisory-group evaluation, and production-grade safety controls are not implemented. Medical steps must be reviewed by curriculum and domain experts before being added to the Agent's scenario knowledge.
- The Agent's environment suggestions use deterministic rules and are applied explicitly by the user in Unity. The model cannot directly manipulate the scene.

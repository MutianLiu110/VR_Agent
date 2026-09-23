using System;
using System.Collections;
using System.IO;
using System.Text;
using UnityEngine;
using UnityEngine.Networking;

// Desktop integration harness: the capsule is a visible Agent placeholder.
// Replace OnGUI with a world-space VR canvas when integrating into the real scenario.
public class VrAgentDemoClient : MonoBehaviour
{
    [Header("Backend (use your computer's LAN IP on a standalone headset)")]
    [SerializeField] private string backendBaseUrl = "http://localhost:8080"; // 后端地址
    [Header("Optional environmental sound")]
    [SerializeField] private AudioSource backgroundAudio;

    private string sessionId;
    private string stage;
    private string pendingAdjustment;
    private string question = "The sound is too loud. What should I do next?";
    private string agentMessage = "Connecting to backend...";
    private string suggestionMessage = "No suggestion yet.";

    [Serializable]
    private class SessionView
    {
        public string sessionId;
        public string stage;
    }

    [Serializable]
    private class MessageRequest
    {
        public string question;
        public string answerLength = "short";
    }

    [Serializable]
    private class AgentReply
    {
        public string reply;
        public string suggestedAdjustment;
        public string mode;
    }

    private void Start()
    {
        LoadBackendConfiguration();
        StartCoroutine(CreateSession());
    }

    private void OnGUI()
    {
        GUILayout.BeginArea(new Rect(20, 20, 540, 310), GUI.skin.box);
        GUILayout.Label("VR Agent three-part integration demo");
        GUILayout.Label("Backend: " + backendBaseUrl);
        GUILayout.Label("Stage: " + (string.IsNullOrEmpty(stage) ? "connecting" : stage));
        GUILayout.Label("Ask the Agent:");
        question = GUILayout.TextField(question);
        GUILayout.BeginHorizontal();
        if (GUILayout.Button("Ask Agent")) AskAgent();
        if (GUILayout.Button("Advance stage")) AdvanceScenario();
        if (GUILayout.Button("Apply suggestion")) ApplySuggestedAdjustment();
        GUILayout.EndHorizontal();
        GUILayout.Label("Agent: " + agentMessage);
        GUILayout.Label("Environment: " + suggestionMessage);
        GUILayout.EndArea();
    }

    public void AskAgent()
    {
        if (string.IsNullOrWhiteSpace(question))
        {
            ShowAgent("Enter a question first.");
            return;
        }
        if (string.IsNullOrEmpty(sessionId))
        {
            ShowAgent("Still connecting to the backend.");
            return;
        }
        StartCoroutine(PostMessage(question));
    }

    // Connect this to the Advance button; in the real VR scene call it after a task is completed.
    public void AdvanceScenario()
    {
        if (!string.IsNullOrEmpty(sessionId))
        {
            StartCoroutine(Advance());
        }
    }

    // The user chooses whether to apply the Agent's suggestion.
    public void ApplySuggestedAdjustment()
    {
        if (pendingAdjustment == "REDUCE_BACKGROUND_SOUND")
        {
            if (backgroundAudio != null)
            {
                backgroundAudio.volume = 0.25f;
                ShowSuggestion("Background sound lowered to 25%.");
            }
            else
            {
                ShowSuggestion("Sound preference set. Connect a background AudioSource to hear the change.");
            }
            pendingAdjustment = null;
        }
    }

    private IEnumerator CreateSession()
    {
        using (UnityWebRequest request = NewPost("/api/sessions", "{}"))
        {
            yield return request.SendWebRequest();
            if (!Succeeded(request)) yield break;
            SessionView session = JsonUtility.FromJson<SessionView>(request.downloadHandler.text);
            sessionId = session.sessionId;
            stage = session.stage;
            ShowAgent("Connected. Ask the Agent a question.");
        }
    }

    private IEnumerator Advance()
    {
        using (UnityWebRequest request = NewPost("/api/sessions/" + sessionId + "/events/advance", "{}"))
        {
            yield return request.SendWebRequest();
            if (!Succeeded(request)) yield break;
            SessionView session = JsonUtility.FromJson<SessionView>(request.downloadHandler.text);
            stage = session.stage;
        }
    }

    private IEnumerator PostMessage(string question)
    {
        MessageRequest body = new MessageRequest { question = question };
        using (UnityWebRequest request = NewPost(
            "/api/sessions/" + sessionId + "/messages", JsonUtility.ToJson(body)))
        {
            ShowAgent("Agent is thinking...");
            yield return request.SendWebRequest();
            if (request.responseCode == 504)
            {
                AgentReply timeout = JsonUtility.FromJson<AgentReply>(request.downloadHandler.text);
                pendingAdjustment = null;
                ShowAgent(timeout != null && !string.IsNullOrEmpty(timeout.reply)
                    ? timeout.reply + "  [timeout]"
                    : "Agent request timed out.");
                ShowSuggestion("No environment adjustment while the Agent is unavailable.");
                yield break;
            }
            if (!Succeeded(request)) yield break;
            AgentReply answer = JsonUtility.FromJson<AgentReply>(request.downloadHandler.text);
            ShowAgent(answer.reply + "  [" + answer.mode + "]");
            pendingAdjustment = answer.suggestedAdjustment;
            ShowSuggestion(pendingAdjustment == "REDUCE_BACKGROUND_SOUND"
                ? "Suggested: reduce background sound. Click Apply to accept."
                : "No environment adjustment suggested.");
        }
    }

    private UnityWebRequest NewPost(string path, string json)
    {
        UnityWebRequest request = new UnityWebRequest(backendBaseUrl.TrimEnd('/') + path, "POST");
        request.uploadHandler = new UploadHandlerRaw(Encoding.UTF8.GetBytes(json));
        request.downloadHandler = new DownloadHandlerBuffer();
        request.SetRequestHeader("Content-Type", "application/json");
        request.timeout = 35;
        return request;
    }

    private void LoadBackendConfiguration()
    {
        const string argumentPrefix = "--backend-url=";
        foreach (string argument in Environment.GetCommandLineArgs())
        {
            if (argument.StartsWith(argumentPrefix, StringComparison.OrdinalIgnoreCase))
            {
                SetBackendUrl(argument.Substring(argumentPrefix.Length), "command line");
                return;
            }
        }

        string configPath = Path.Combine(Application.streamingAssetsPath, "backend-url.txt");
        if (File.Exists(configPath))
        {
            SetBackendUrl(File.ReadAllText(configPath), configPath);
        }
    }

    private void SetBackendUrl(string value, string source)
    {
        string candidate = (value ?? string.Empty).Trim().TrimEnd('/');
        if (Uri.TryCreate(candidate, UriKind.Absolute, out Uri uri)
            && (uri.Scheme == Uri.UriSchemeHttp || uri.Scheme == Uri.UriSchemeHttps))
        {
            backendBaseUrl = candidate;
            Debug.Log("VR Agent backend loaded from " + source + ": " + backendBaseUrl);
        }
        else
        {
            Debug.LogWarning("Ignored invalid VR Agent backend URL from " + source);
        }
    }

    private bool Succeeded(UnityWebRequest request)
    {
        if (request.result == UnityWebRequest.Result.Success) return true;
        ShowAgent(request.responseCode == 0
            ? "Cannot reach the Java backend. Check that it is running on port 8080."
            : "Request failed (HTTP " + request.responseCode + "): " + request.error);
        Debug.LogError(request.downloadHandler != null ? request.downloadHandler.text : request.error);
        return false;
    }

    private void ShowAgent(string message)
    {
        agentMessage = message;
        Debug.Log("VR Agent: " + message);
    }

    private void ShowSuggestion(string message)
    {
        suggestionMessage = message;
        Debug.Log("VR suggestion: " + message);
    }
}

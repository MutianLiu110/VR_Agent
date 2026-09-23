using System.IO;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.SceneManagement;

public static class DemoSceneBuilder
{
    private const string ScenePath = "Assets/Scenes/Demo.unity";

    // Create the scene on first import without replacing the user's open scene.
    [InitializeOnLoadMethod]
    private static void CreateMissingSceneOnImport()
    {
        EditorApplication.delayCall += () =>
        {
            if (File.Exists(ScenePath)) return;

            Scene previous = SceneManager.GetActiveScene();
            Scene demo = EditorSceneManager.NewScene(NewSceneSetup.EmptyScene, NewSceneMode.Additive);
            SceneManager.SetActiveScene(demo);
            BuildContents();
            SaveAndRegister(demo);

            if (previous.IsValid() && previous.isLoaded)
            {
                SceneManager.SetActiveScene(previous);
                EditorSceneManager.CloseScene(demo, true);
            }

            Debug.Log("VR Agent demo scene created. Open Assets/Scenes/Demo.unity to see it.");
        };
    }

    [MenuItem("Tools/VR Agent/Open Demo Scene")]
    public static void OpenDemoScene()
    {
        if (!File.Exists(ScenePath))
        {
            Scene demo = EditorSceneManager.NewScene(NewSceneSetup.EmptyScene, NewSceneMode.Single);
            BuildContents();
            SaveAndRegister(demo);
        }
        else
        {
            EditorSceneManager.OpenScene(ScenePath, OpenSceneMode.Single);
        }
    }

    [MenuItem("Tools/VR Agent/Create Demo Scene")]
    public static void CreateDemoScene()
    {
        OpenDemoScene();
    }

    private static void BuildContents()
    {
        GameObject floor = GameObject.CreatePrimitive(PrimitiveType.Plane);
        floor.name = "Training room floor";
        floor.transform.localScale = new Vector3(1.6f, 1f, 1.2f);

        CreateBox("Back wall", new Vector3(0f, 1.5f, 5.8f), new Vector3(16f, 3f, 0.2f));
        CreateBox("Left wall", new Vector3(-8f, 1.5f, 0f), new Vector3(0.2f, 3f, 12f));
        CreateBox("Right wall", new Vector3(8f, 1.5f, 0f), new Vector3(0.2f, 3f, 12f));

        CreateBox("Training bed frame", new Vector3(-2.2f, 0.45f, 1.5f), new Vector3(2.4f, 0.35f, 4f));
        CreateBox("Mattress", new Vector3(-2.2f, 0.7f, 1.5f), new Vector3(2.2f, 0.2f, 3.8f));
        CreateBox("Pillow", new Vector3(-2.2f, 0.85f, 2.8f), new Vector3(1.4f, 0.15f, 0.5f));

        GameObject patient = GameObject.CreatePrimitive(PrimitiveType.Capsule);
        patient.name = "Patient placeholder";
        patient.transform.position = new Vector3(-2.2f, 1.1f, 1f);
        patient.transform.rotation = Quaternion.Euler(90f, 0f, 0f);
        patient.transform.localScale = new Vector3(0.65f, 1f, 0.65f);

        CreateBox("Bedside monitor stand", new Vector3(-4.2f, 0.8f, 2f), new Vector3(0.2f, 1.6f, 0.2f));
        CreateBox("Bedside monitor", new Vector3(-4.2f, 1.75f, 2f), new Vector3(0.8f, 0.7f, 0.2f));

        GameObject agent = GameObject.CreatePrimitive(PrimitiveType.Capsule);
        agent.name = "Agent placeholder (API client)";
        agent.transform.position = new Vector3(2f, 1f, 1.5f);
        agent.AddComponent<VrAgentDemoClient>();

        GameObject cameraObject = new GameObject("Main Camera");
        cameraObject.tag = "MainCamera";
        Camera camera = cameraObject.AddComponent<Camera>();
        cameraObject.AddComponent<AudioListener>();
        camera.transform.position = new Vector3(0f, 2.5f, -5.7f);
        camera.transform.LookAt(new Vector3(0f, 1f, 1.5f));
        camera.clearFlags = CameraClearFlags.Skybox;

        GameObject lightObject = new GameObject("Directional Light");
        Light light = lightObject.AddComponent<Light>();
        light.type = LightType.Directional;
        light.intensity = 1.2f;
        lightObject.transform.rotation = Quaternion.Euler(45f, -30f, 0f);
    }

    private static void CreateBox(string name, Vector3 position, Vector3 scale)
    {
        GameObject box = GameObject.CreatePrimitive(PrimitiveType.Cube);
        box.name = name;
        box.transform.position = position;
        box.transform.localScale = scale;
    }

    private static void SaveAndRegister(Scene scene)
    {
        Directory.CreateDirectory("Assets/Scenes");
        EditorSceneManager.SaveScene(scene, ScenePath);
        EditorBuildSettings.scenes = new[] { new EditorBuildSettingsScene(ScenePath, true) };
        AssetDatabase.Refresh();
    }
}

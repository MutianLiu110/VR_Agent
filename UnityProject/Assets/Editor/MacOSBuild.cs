using System;
using System.IO;
using System.Linq;
using UnityEditor;
using UnityEditor.Build.Reporting;
using UnityEngine;

public static class MacOSBuild
{
    [MenuItem("Tools/VR Agent/Build macOS Demo")]
    public static void Build()
    {
        string[] scenes = EditorBuildSettings.scenes
            .Where(scene => scene.enabled)
            .Select(scene => scene.path)
            .ToArray();
        if (scenes.Length == 0)
        {
            throw new InvalidOperationException("No enabled scene is configured for the build.");
        }

        Directory.CreateDirectory("Builds/macOS");
        BuildReport report = BuildPipeline.BuildPlayer(new BuildPlayerOptions
        {
            scenes = scenes,
            locationPathName = "Builds/macOS/VRAgentDemo.app",
            target = BuildTarget.StandaloneOSX,
            options = BuildOptions.None,
        });

        if (report.summary.result != BuildResult.Succeeded)
        {
            throw new InvalidOperationException("macOS build failed: " + report.summary.result);
        }
        Debug.Log("macOS demo built at Builds/macOS/VRAgentDemo.app");
    }
}

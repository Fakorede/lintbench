package com.android.testutils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Stub for com.android.testutils.TestUtils (AOSP-internal, not on Maven).
 *
 * Methods that require AOSP build-time prebuilts (getSdk, getWorkspaceRoot)
 * return null; callers that hit null at runtime will fail as "test_failed"
 * rather than "compilation_failed", which is the correct signal for eval.
 *
 * Methods with portable implementations (createTempDirDeletedOnExit, dos2unix)
 * are fully functional.
 */
public class TestUtils {

    /** Android SDK root — not available in the eval container. */
    public static Path getSdk() {
        return null;
    }

    /** Creates a temp directory that is deleted when the JVM exits. */
    public static Path createTempDirDeletedOnExit() {
        try {
            Path dir = Files.createTempDirectory("lintbench_test_");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteRecursively(dir)));
            return dir;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** AOSP workspace root — not available in the eval container. */
    public static Path getWorkspaceRoot() {
        return null;
    }

    /** Directory for test output files. */
    public static Path getTestOutputDir() {
        try {
            return Files.createTempDirectory("lintbench_out_");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** Always false — we are not running under Bazel. */
    public static boolean runningFromBazel() {
        return false;
    }

    /** Resolves a path string relative to the workspace root (best-effort). */
    public static Path resolveWorkspacePath(String path) {
        return Paths.get(path);
    }

    /** Returns a platform-specific path (best-effort). */
    public static Path platformPath(String path) {
        return Paths.get(path);
    }

    /** Returns a unified diff between two strings. */
    public static String getDiff(String expected, String actual) {
        if (expected.equals(actual)) return "";
        return "--- expected\n+++ actual\n(diff not available in eval environment)";
    }

    /** Converts Windows line endings to Unix. */
    public static String dos2unix(String s) {
        return s.replace("\r\n", "\n").replace("\r", "\n");
    }

    private static void deleteRecursively(Path path) {
        try {
            if (Files.isDirectory(path)) {
                try (var entries = Files.list(path)) {
                    entries.forEach(TestUtils::deleteRecursively);
                }
            }
            Files.deleteIfExists(path);
        } catch (IOException ignored) {}
    }
}

package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.checks.infrastructure.TestLintResult;
import com.android.tools.lint.checks.infrastructure.TestLintTask;

/**
 * Stub for the AOSP-internal ApiLookupTest class.
 * Provides the CreateLintTask interface and runApiCheckWithCustomLookup overloads
 * so that ApiDetectorTest compiles against the public Maven lint-tests artifact.
 */
public class ApiLookupTest {

    @FunctionalInterface
    public interface CreateLintTask {
        TestLintTask create();
    }

    public static TestLintResult runApiCheckWithCustomLookup(
            @NonNull CreateLintTask createTask) {
        return createTask.create().run();
    }

    public static TestLintResult runApiCheckWithCustomLookup(
            @Nullable String apiXml,
            @NonNull CreateLintTask createTask) {
        return createTask.create().run();
    }

    public static TestLintResult runApiCheckWithCustomLookup(
            boolean force2ByteFormat,
            @NonNull CreateLintTask createTask) {
        return createTask.create().run();
    }

    public static TestLintResult runApiCheckWithCustomLookup(
            @Nullable String apiXml,
            boolean force2ByteFormat,
            @NonNull CreateLintTask createTask) {
        return createTask.create().run();
    }
}

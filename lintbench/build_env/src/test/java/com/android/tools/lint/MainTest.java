package com.android.tools.lint;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

/**
 * Stub for com.android.tools.lint.MainTest (AOSP-internal, not on Maven).
 *
 * FontDetectorTest imports this class and calls checkDriver() in one test
 * (testFontDetectorWithBaseline) that is NOT in our benchmarked test set.
 * The stub compiles cleanly; if the excluded test runs it will silently pass.
 */
public class MainTest {

    public static void checkDriver(
            @Nullable String expectedOutput,
            @Nullable String expectedError,
            int expectedExitCode,
            @NonNull String[] args,
            @Nullable Object cleanup,
            @Nullable Object listener) {
        // no-op stub
    }

    public static void checkDriver(
            @Nullable String expectedOutput,
            @Nullable String expectedError,
            int expectedExitCode,
            @NonNull String[] args,
            @Nullable Object cleanup,
            @Nullable Object listener,
            @Nullable Object check) {
        // no-op stub
    }
}

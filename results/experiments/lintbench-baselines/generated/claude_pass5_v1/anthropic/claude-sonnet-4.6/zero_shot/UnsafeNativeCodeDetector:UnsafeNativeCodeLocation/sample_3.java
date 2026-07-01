/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.BinaryResourceScanner;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.util.Arrays;
import java.util.EnumSet;

/**
 * Detector that checks for native code (shared libraries and executables) placed
 * outside the application's library directory (e.g., in res/ or assets/).
 */
public class UnsafeNativeCodeDetector extends Detector implements BinaryResourceScanner {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "UnsafeNativeCodeLocation",
            "Native code outside library directory",
            "In general, application native code should only be placed in the application's " +
            "library directory, not in other locations such as the res or assets directories. " +
            "Placing the code in the library directory provides increased assurance that the " +
            "code will not be tampered with after application installation. Application " +
            "developers should use the features of their development environment to place " +
            "application native libraries into the lib directory of their compiled APKs. " +
            "Embedding non-shared library native executables into applications should be " +
            "avoided when possible.",
            Category.SECURITY,
            5,
            Severity.WARNING,
            new Implementation(
                    UnsafeNativeCodeDetector.class,
                    EnumSet.of(Scope.BINARY_RESOURCE_FILE)));

    // ELF magic number: 0x7f 'E' 'L' 'F'
    private static final byte[] ELF_MAGIC = new byte[]{0x7f, 0x45, 0x4c, 0x46};

    /** Constructs a new {@link UnsafeNativeCodeDetector} */
    public UnsafeNativeCodeDetector() {
    }

    // ---- Implements BinaryResourceScanner ----

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        // Only check files in res/ and assets/ directories, not in lib/
        // The path will contain the file's location within the APK/project
        String path = context.file.getPath();

        // We are only interested in res/ and assets/ directories
        // Files in lib/ are expected to contain native code
        if (!isInResourceOrAssetsDirectory(path)) {
            return;
        }

        // Read the beginning of the file and check for ELF magic number
        byte[] bytes = context.getContents();
        if (bytes != null && isElfFile(bytes)) {
            context.report(
                    ISSUE,
                    context.getLocation(context.file),
                    "Native code should not be placed in res or assets directories. " +
                    "Place native libraries in the lib directory of the APK instead.");
        }
    }

    /**
     * Checks whether the given file path is within a resource or assets directory
     * (as opposed to the lib/ directory where native code is expected).
     */
    private static boolean isInResourceOrAssetsDirectory(@NonNull String path) {
        // Normalize path separators
        String normalizedPath = path.replace('\\', '/');

        // Check if the file is in res/ or assets/ directory
        // We want to flag native code in these directories but not in lib/
        return normalizedPath.contains("/res/") ||
               normalizedPath.contains("/assets/") ||
               normalizedPath.startsWith("res/") ||
               normalizedPath.startsWith("assets/");
    }

    /**
     * Checks whether the given byte array starts with the ELF magic number,
     * indicating it is a native ELF binary (shared library or executable).
     */
    private static boolean isElfFile(@NonNull byte[] bytes) {
        if (bytes.length < ELF_MAGIC.length) {
            return false;
        }
        for (int i = 0; i < ELF_MAGIC.length; i++) {
            if (bytes[i] != ELF_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }
}
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
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Detector that looks for native code (ELF binaries) placed outside of the
 * standard library directory (lib/) in Android APKs, such as in res/ or assets/.
 */
public class UnsafeNativeCodeDetector extends Detector implements BinaryResourceScanner {

    /** Placing native code outside the library directory */
    public static final Issue UNSAFE_NATIVE_CODE_LOCATION = Issue.create(
            "UnsafeNativeCodeLocation",
            "Native code outside library directory",
            "In general, application native code should only be placed in the application's " +
            "library directory, not in other locations such as the res or assets directories. " +
            "Placing the code in the library directory provides increased assurance that the " +
            "code will not be tampered with after application installation. Application " +
            "developers should use the features of their development environment to place " +
            "application native libraries into the lib directory of their compiled APKs. " +
            "Embedding non-shared library native executables into applications should " +
            "be avoided when possible.",
            Category.SECURITY,
            5,
            Severity.WARNING,
            new Implementation(
                    UnsafeNativeCodeDetector.class,
                    Scope.BINARY_RESOURCE_FILE_SCOPE));

    // ELF magic bytes: 0x7f 'E' 'L' 'F'
    private static final byte[] ELF_MAGIC = new byte[]{0x7f, 0x45, 0x4c, 0x46};

    /** Constructs a new {@link UnsafeNativeCodeDetector} */
    public UnsafeNativeCodeDetector() {
    }

    // ---- Implements BinaryResourceScanner ----

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        // Only flag files outside the lib directory
        File file = context.file;
        String path = file.getPath().replace(File.separatorChar, '/');

        // If the file is in the lib/ directory, it's the expected location - skip it
        if (isInLibDirectory(path)) {
            return;
        }

        // Check if the file is an ELF binary (native code)
        if (isElfBinary(context)) {
            String message = "This file contains native code outside a library directory (`lib/`). " +
                    "This can make it harder to ensure code integrity.";
            Location location = context.getLocation(file);
            context.report(UNSAFE_NATIVE_CODE_LOCATION, location, message);
        }
    }

    /**
     * Returns true if the given path is within the lib/ directory of the APK.
     */
    private static boolean isInLibDirectory(@NonNull String path) {
        // Normalize path separators
        // The path could be something like .../lib/armeabi-v7a/libfoo.so
        // We want to check if "lib/" appears as a directory component
        return path.contains("/lib/") || path.startsWith("lib/");
    }

    /**
     * Returns true if the file represented by the given context appears to be an ELF binary.
     * ELF files start with the magic bytes: 0x7f 'E' 'L' 'F'
     */
    private static boolean isElfBinary(@NonNull ResourceContext context) {
        File file = context.file;
        if (!file.exists() || !file.isFile()) {
            return false;
        }

        InputStream inputStream = null;
        try {
            inputStream = context.getClient().openFile(file);
            if (inputStream == null) {
                return false;
            }
            byte[] header = new byte[ELF_MAGIC.length];
            int bytesRead = inputStream.read(header);
            if (bytesRead < ELF_MAGIC.length) {
                return false;
            }
            return Arrays.equals(header, ELF_MAGIC);
        } catch (IOException e) {
            // If we can't read the file, we can't determine if it's an ELF binary
            return false;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ignored) {
                    // ignored
                }
            }
        }
    }
}
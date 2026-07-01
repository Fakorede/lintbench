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
 * Detector that checks for native code placed outside the library directory.
 */
public class UnsafeNativeCodeDetector extends Detector implements BinaryResourceScanner {

    /** Native code outside library directory */
    public static final Issue ISSUE = Issue.create(
            "UnsafeNativeCodeLocation",
            "Native code outside library directory",
            "In general, application native code should only be placed in the application's " +
            "library directory, not in other locations such as the `res` or `assets` directories. " +
            "Placing the code in the library directory provides increased assurance that the " +
            "code will not be tampered with after application installation. Application " +
            "developers should use the features of their development environment to place " +
            "application native libraries into the `lib` directory of their compiled APKs. " +
            "Embedding non-shared library native executables into applications should " +
            "be avoided when possible.",
            Category.SECURITY,
            5,
            Severity.WARNING,
            new Implementation(
                    UnsafeNativeCodeDetector.class,
                    Scope.BINARY_RESOURCE_FILE_SCOPE));

    // ELF magic number: 0x7f 'E' 'L' 'F'
    private static final byte[] ELF_MAGIC = new byte[]{0x7f, 0x45, 0x4c, 0x46};

    // DEX magic number: 'dex\n'
    private static final byte[] DEX_MAGIC = new byte[]{0x64, 0x65, 0x78, 0x0a};

    private static final int MAGIC_SIZE = 4;

    public UnsafeNativeCodeDetector() {
    }

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        // Only check files in res/ or assets/ directories, not in lib/
        File file = context.file;
        String path = file.getPath();

        // Normalize path separators
        path = path.replace('\\', '/');

        // Check if the file is in the lib directory - if so, it's fine
        if (isInLibDirectory(path)) {
            return;
        }

        // Check if the file has native code magic bytes
        if (hasNativeCodeMagic(file)) {
            String message;
            if (path.contains("/res/") || path.contains("/assets/")) {
                message = "This file should not be placed in the `res` or `assets` directory. " +
                        "Native code should be placed in the application's library directory.";
            } else {
                message = "This file should not contain native code. " +
                        "Native code should be placed in the application's library directory.";
            }
            context.report(ISSUE, Location.create(file), message);
        }
    }

    /**
     * Checks whether the given file path is within a lib directory.
     */
    private static boolean isInLibDirectory(@NonNull String path) {
        return path.contains("/lib/") || path.contains("/libs/");
    }

    /**
     * Reads the first few bytes of the file and checks if they match known
     * native code magic numbers (ELF or DEX).
     */
    private static boolean hasNativeCodeMagic(@NonNull File file) {
        InputStream inputStream = null;
        try {
            inputStream = new java.io.FileInputStream(file);
            byte[] magic = new byte[MAGIC_SIZE];
            int bytesRead = inputStream.read(magic);
            if (bytesRead < MAGIC_SIZE) {
                return false;
            }
            return isElfFile(magic) || isDexFile(magic);
        } catch (IOException e) {
            return false;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ignored) {
                    // Ignore
                }
            }
        }
    }

    /**
     * Returns true if the magic bytes match an ELF file.
     */
    private static boolean isElfFile(@NonNull byte[] magic) {
        return Arrays.equals(magic, ELF_MAGIC);
    }

    /**
     * Returns true if the magic bytes match a DEX file.
     */
    private static boolean isDexFile(@NonNull byte[] magic) {
        return Arrays.equals(magic, DEX_MAGIC);
    }
}
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
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.EnumSet;

/**
 * Detector that checks for native code placed outside the library directory.
 */
public class UnsafeNativeCodeDetector extends Detector implements BinaryResourceScanner {

    /** Native code outside library directory */
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

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        File file = context.file;
        String path = file.getPath().replace('\\', '/');

        // Files in lib/ are expected to contain native code - skip them
        if (path.contains("/lib/") || path.contains("/libs/")) {
            return;
        }

        // We only want to flag files in res/ or assets/
        boolean inResOrAssets = path.contains("/res/") || path.contains("/assets/");
        if (!inResOrAssets) {
            return;
        }

        // Read the beginning of the file to check for ELF magic number
        byte[] header = readFileHeader(file, ELF_MAGIC.length);
        if (header == null || header.length < ELF_MAGIC.length) {
            return;
        }

        // Check for ELF magic number
        if (isElfFile(header)) {
            context.report(
                    ISSUE,
                    Location.create(file),
                    "Native code should not be placed in the res or assets directory. " +
                    "Place it in the lib directory instead.");
        }
    }

    /**
     * Reads the first {@code length} bytes from the given file.
     *
     * @param file   the file to read
     * @param length the number of bytes to read
     * @return the bytes read, or null if an error occurred
     */
    private static byte[] readFileHeader(@NonNull File file, int length) {
        byte[] buffer = new byte[length];
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
            int read = fis.read(buffer, 0, length);
            if (read < length) {
                return null;
            }
            return buffer;
        } catch (IOException e) {
            return null;
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * Checks whether the given byte array starts with the ELF magic number.
     *
     * @param bytes the bytes to check
     * @return true if the bytes start with the ELF magic number
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
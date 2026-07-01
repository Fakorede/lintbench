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
import java.util.EnumSet;

/**
 * Detector that looks for native code (ELF binaries) placed outside of the
 * application's library directory (i.e., in res/ or assets/).
 */
public class UnsafeNativeCodeDetector extends Detector implements BinaryResourceScanner {

    /** Placing native code outside the lib directory */
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
                    EnumSet.of(Scope.BINARY_RESOURCE_FILE)));

    // ELF magic number: 0x7f 'E' 'L' 'F'
    private static final byte[] ELF_MAGIC = new byte[]{0x7f, 0x45, 0x4c, 0x46};

    /** Constructs a new {@link UnsafeNativeCodeDetector} */
    public UnsafeNativeCodeDetector() {
    }

    // ---- Implements BinaryResourceScanner ----

    @Override
    public void checkBinaryResource(@NonNull ResourceContext context) {
        // We only care about files in res/ or assets/ directories
        // (not in lib/ which is the correct location)
        File file = context.file;
        String path = file.getPath().replace(File.separatorChar, '/');

        // Check if this file is in a res or assets directory (not lib)
        if (!isInResourceOrAssetDirectory(path)) {
            return;
        }

        // Check if the file is an ELF binary
        if (isElfBinary(file)) {
            String message = "This file should not be placed in a res or assets directory. " +
                    "Application native libraries should be placed in the lib directory " +
                    "of the compiled APK.";
            Location location = context.getLocation(file);
            context.report(UNSAFE_NATIVE_CODE_LOCATION, location, message);
        }
    }

    /**
     * Returns true if the given path is in a res or assets directory
     * (and not in the lib directory).
     */
    private static boolean isInResourceOrAssetDirectory(@NonNull String path) {
        // Check for res/ or assets/ in the path
        // We want to flag files in res/ and assets/ but not in lib/
        boolean inRes = path.contains("/res/") || path.contains("\\res\\");
        boolean inAssets = path.contains("/assets/") || path.contains("\\assets\\");
        return inRes || inAssets;
    }

    /**
     * Returns true if the given file appears to be an ELF binary by checking
     * its magic number.
     */
    private static boolean isElfBinary(@NonNull File file) {
        try (InputStream is = new java.io.FileInputStream(file)) {
            byte[] magic = new byte[ELF_MAGIC.length];
            int bytesRead = is.read(magic);
            if (bytesRead < ELF_MAGIC.length) {
                return false;
            }
            return Arrays.equals(magic, ELF_MAGIC);
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public void checkResource(@NonNull ResourceContext context) {
        // Not used; we use checkBinaryResource instead
    }
}
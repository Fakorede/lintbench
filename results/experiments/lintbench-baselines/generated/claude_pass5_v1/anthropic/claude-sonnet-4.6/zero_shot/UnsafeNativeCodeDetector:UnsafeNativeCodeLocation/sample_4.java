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
        // Only check files in res/ and assets/ directories (not in lib/)
        File file = context.file;
        String path = file.getPath().replace(File.separatorChar, '/');

        // Check if this is in the assets or res directory (not lib)
        if (!isInAssetsOrRes(path)) {
            return;
        }

        // Check if this file is an ELF binary (native code)
        if (isElfBinary(context)) {
            String message = String.format(
                    "This file should not be placed in a `res` or `assets` directory. " +
                    "Native code should be placed in the application's library directory " +
                    "(`lib/`). Placing native code in other directories may allow it to " +
                    "be tampered with after installation.");
            context.report(ISSUE, Location.create(file), message);
        }
    }

    /**
     * Checks whether the given path is within assets or res directories.
     */
    private static boolean isInAssetsOrRes(@NonNull String path) {
        // Check for assets/ or res/ directory components in the path
        return path.contains("/assets/") || path.contains("/res/")
                || path.startsWith("assets/") || path.startsWith("res/");
    }

    /**
     * Checks whether the file represented by the given context is an ELF binary.
     */
    private static boolean isElfBinary(@NonNull ResourceContext context) {
        InputStream stream = null;
        try {
            stream = context.getClient().openResource(context.file);
            if (stream == null) {
                return false;
            }
            byte[] header = new byte[ELF_MAGIC.length];
            int bytesRead = readFully(stream, header);
            if (bytesRead < ELF_MAGIC.length) {
                return false;
            }
            return Arrays.equals(header, ELF_MAGIC);
        } catch (IOException e) {
            return false;
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ignore) {
                    // ignore
                }
            }
        }
    }

    /**
     * Reads exactly {@code buf.length} bytes from the stream into buf.
     * Returns the number of bytes actually read.
     */
    private static int readFully(@NonNull InputStream stream, @NonNull byte[] buf)
            throws IOException {
        int offset = 0;
        int remaining = buf.length;
        while (remaining > 0) {
            int n = stream.read(buf, offset, remaining);
            if (n < 0) {
                break;
            }
            offset += n;
            remaining -= n;
        }
        return offset;
    }
}
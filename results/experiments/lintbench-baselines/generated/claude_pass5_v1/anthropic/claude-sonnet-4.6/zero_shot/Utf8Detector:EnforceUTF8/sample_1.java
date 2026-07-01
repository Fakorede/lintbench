/*
 * Copyright (C) 2012 The Android Open Source Project
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
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Document;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Checks that resource XML files are using UTF-8 encoding.
 */
public class Utf8Detector extends ResourceXmlDetector {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "EnforceUTF8",
            "Encoding used in resource files is not UTF-8",
            "XML supports encoding in a wide variety of character sets. However, not all " +
            "tools handle the XML encoding attribute correctly, and nearly all Android " +
            "apps use UTF-8, so by using UTF-8 you can protect yourself against subtle " +
            "bugs when using non-ASCII characters.\n" +
            "\n" +
            "In particular, the Android Gradle build system will merge resource XML files " +
            "assuming the resource files are using UTF-8 encoding.",
            Category.I18N,
            8,
            Severity.ERROR,
            new Implementation(
                    Utf8Detector.class,
                    Scope.RESOURCE_FILE_SCOPE));

    /** Constructs a new {@link Utf8Detector} */
    public Utf8Detector() {
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        // Check the file's encoding by reading the raw bytes and looking for the
        // XML declaration encoding attribute.
        File file = context.file;
        String encoding = getEncoding(file);

        if (encoding != null && !encoding.equalsIgnoreCase("utf-8")
                && !encoding.equalsIgnoreCase("utf8")) {
            Location location = Location.create(file);
            context.report(ISSUE, location,
                    String.format("Resource files should be in UTF-8 encoding; found `%1$s` instead.",
                            encoding));
        } else if (encoding == null) {
            // Check if the file has a BOM that indicates a non-UTF-8 encoding
            String bom = getBomEncoding(file);
            if (bom != null && !bom.equalsIgnoreCase("utf-8") && !bom.equalsIgnoreCase("utf8")) {
                Location location = Location.create(file);
                context.report(ISSUE, location,
                        String.format("Resource files should be in UTF-8 encoding; found `%1$s` instead.",
                                bom));
            }
        }
    }

    /**
     * Reads the XML declaration from the file to extract the encoding attribute.
     *
     * @param file the file to check
     * @return the encoding string if found, or null if not specified
     */
    private static String getEncoding(File file) {
        try {
            InputStream input = new BufferedInputStream(new FileInputStream(file));
            try {
                byte[] buf = new byte[200];
                int count = input.read(buf);
                if (count < 5) {
                    return null;
                }

                // Check for BOM markers and adjust starting position
                int start = 0;
                if (buf[0] == (byte) 0xEF && buf[1] == (byte) 0xBB && buf[2] == (byte) 0xBF) {
                    // UTF-8 BOM
                    start = 3;
                } else if ((buf[0] == (byte) 0xFE && buf[1] == (byte) 0xFF)
                        || (buf[0] == (byte) 0xFF && buf[1] == (byte) 0xFE)) {
                    // UTF-16 BOM - can't easily read as ASCII, skip
                    return null;
                } else if (buf[0] == 0 && buf[1] == (byte) '<') {
                    // Big-endian UTF-16 or UTF-32 without BOM
                    return null;
                } else if (buf[0] == (byte) '<' && buf[1] == 0) {
                    // Little-endian UTF-16 without BOM
                    return null;
                }

                // Look for XML declaration: <?xml ... encoding="..." ?>
                // Convert to string for easier parsing (assuming ASCII-compatible encoding)
                String header = new String(buf, start, count - start, "US-ASCII");

                if (!header.startsWith("<?xml")) {
                    return null;
                }

                // Find encoding attribute
                int encodingIndex = header.indexOf("encoding");
                if (encodingIndex == -1) {
                    return null;
                }

                // Find the value after encoding=
                int eqIndex = header.indexOf('=', encodingIndex + 8);
                if (eqIndex == -1) {
                    return null;
                }

                // Skip whitespace after =
                int valueStart = eqIndex + 1;
                while (valueStart < header.length() && Character.isWhitespace(header.charAt(valueStart))) {
                    valueStart++;
                }

                if (valueStart >= header.length()) {
                    return null;
                }

                char quote = header.charAt(valueStart);
                if (quote != '\'' && quote != '"') {
                    return null;
                }

                int valueEnd = header.indexOf(quote, valueStart + 1);
                if (valueEnd == -1) {
                    return null;
                }

                return header.substring(valueStart + 1, valueEnd);
            } finally {
                input.close();
            }
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Checks for BOM markers that indicate specific encodings.
     *
     * @param file the file to check
     * @return the encoding indicated by BOM, or null if no BOM found
     */
    private static String getBomEncoding(File file) {
        try {
            InputStream input = new BufferedInputStream(new FileInputStream(file));
            try {
                byte[] bom = new byte[4];
                int count = input.read(bom);
                if (count < 2) {
                    return null;
                }

                // UTF-32 BE BOM: 00 00 FE FF
                if (count >= 4
                        && bom[0] == 0x00 && bom[1] == 0x00
                        && bom[2] == (byte) 0xFE && bom[3] == (byte) 0xFF) {
                    return "UTF-32BE";
                }

                // UTF-32 LE BOM: FF FE 00 00
                if (count >= 4
                        && bom[0] == (byte) 0xFF && bom[1] == (byte) 0xFE
                        && bom[2] == 0x00 && bom[3] == 0x00) {
                    return "UTF-32LE";
                }

                // UTF-16 BE BOM: FE FF
                if (bom[0] == (byte) 0xFE && bom[1] == (byte) 0xFF) {
                    return "UTF-16BE";
                }

                // UTF-16 LE BOM: FF FE
                if (bom[0] == (byte) 0xFF && bom[1] == (byte) 0xFE) {
                    return "UTF-16LE";
                }

                // UTF-8 BOM: EF BB BF
                if (count >= 3
                        && bom[0] == (byte) 0xEF
                        && bom[1] == (byte) 0xBB
                        && bom[2] == (byte) 0xBF) {
                    return "UTF-8";
                }

                return null;
            } finally {
                input.close();
            }
        } catch (IOException e) {
            return null;
        }
    }
}
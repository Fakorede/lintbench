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
 * Checks that resource XML files use UTF-8 encoding.
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
        // Check the raw file for the encoding declaration
        String contents = context.getContents();
        if (contents == null) {
            return;
        }

        // Look for the XML declaration: <?xml version="1.0" encoding="..."?>
        // The XML declaration, if present, must be at the very beginning of the file.
        if (!contents.startsWith("<?xml")) {
            // No XML declaration; the default encoding for XML is UTF-8 (or UTF-16 with BOM).
            // Check for BOM or UTF-16 indicators by examining raw bytes.
            checkRawEncoding(context);
            return;
        }

        // Find the end of the XML declaration
        int end = contents.indexOf("?>");
        if (end == -1) {
            return;
        }

        String declaration = contents.substring(0, end + 2);

        // Look for encoding attribute
        int encodingIndex = declaration.indexOf("encoding");
        if (encodingIndex == -1) {
            // No encoding attribute; defaults to UTF-8, which is fine.
            return;
        }

        // Extract the encoding value
        int eqIndex = declaration.indexOf('=', encodingIndex + 8);
        if (eqIndex == -1) {
            return;
        }

        // Skip whitespace after '='
        int valueStart = eqIndex + 1;
        while (valueStart < declaration.length() &&
                Character.isWhitespace(declaration.charAt(valueStart))) {
            valueStart++;
        }

        if (valueStart >= declaration.length()) {
            return;
        }

        char quote = declaration.charAt(valueStart);
        if (quote != '"' && quote != '\'') {
            return;
        }

        int valueEnd = declaration.indexOf(quote, valueStart + 1);
        if (valueEnd == -1) {
            return;
        }

        String encoding = declaration.substring(valueStart + 1, valueEnd);

        if (!encoding.equalsIgnoreCase("utf-8") && !encoding.equalsIgnoreCase("utf8")) {
            // Report the issue
            String message = String.format(
                    "Resource files should be saved with the UTF-8 encoding, not \"%1$s\"",
                    encoding);

            // Try to find the location of the encoding attribute value
            Location location = context.getLocation(document);

            // Attempt to provide a more precise location pointing to the encoding value
            // by searching for the encoding declaration in the source
            int encodingValueOffset = valueStart + 1; // offset into declaration string
            if (encodingValueOffset < contents.length()) {
                location = Location.create(context.file,
                        contents, encodingValueOffset,
                        encodingValueOffset + encoding.length());
            }

            context.report(ISSUE, location, message);
        }
    }

    /**
     * Checks the raw bytes of the file to detect non-UTF-8 encoding via BOM or other markers.
     */
    private void checkRawEncoding(@NonNull XmlContext context) {
        File file = context.file;
        try {
            InputStream stream = new BufferedInputStream(new FileInputStream(file));
            try {
                // Read the first few bytes to check for BOMs
                byte[] bom = new byte[4];
                int read = stream.read(bom, 0, 4);
                if (read < 2) {
                    return;
                }

                // UTF-16 BE BOM: 0xFE 0xFF
                if ((bom[0] & 0xFF) == 0xFE && (bom[1] & 0xFF) == 0xFF) {
                    String message = "Resource files should be saved with the UTF-8 encoding, " +
                            "not UTF-16 (detected from BOM)";
                    context.report(ISSUE, Location.create(file), message);
                    return;
                }

                // UTF-16 LE BOM: 0xFF 0xFE
                if ((bom[0] & 0xFF) == 0xFF && (bom[1] & 0xFF) == 0xFE) {
                    String message = "Resource files should be saved with the UTF-8 encoding, " +
                            "not UTF-16 (detected from BOM)";
                    context.report(ISSUE, Location.create(file), message);
                    return;
                }

                // UTF-32 BE BOM: 0x00 0x00 0xFE 0xFF
                if (read >= 4 &&
                        bom[0] == 0x00 && bom[1] == 0x00 &&
                        (bom[2] & 0xFF) == 0xFE && (bom[3] & 0xFF) == 0xFF) {
                    String message = "Resource files should be saved with the UTF-8 encoding, " +
                            "not UTF-32 (detected from BOM)";
                    context.report(ISSUE, Location.create(file), message);
                }

            } finally {
                try {
                    stream.close();
                } catch (IOException ignore) {
                    // ignore
                }
            }
        } catch (IOException e) {
            // Can't read file; ignore
        }
    }

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return true;
    }
}
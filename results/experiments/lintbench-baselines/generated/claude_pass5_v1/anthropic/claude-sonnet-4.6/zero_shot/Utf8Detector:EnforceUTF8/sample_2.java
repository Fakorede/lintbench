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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;

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
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return true;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        // Check the prolog/encoding of the XML file
        String encoding = document.getXmlEncoding();
        if (encoding != null && !encoding.equalsIgnoreCase("utf-8")) {
            String message = String.format(
                    "Resource files should be encoded in UTF-8 (current encoding is `%1$s`)",
                    encoding);
            Location location = guessEncodingLocation(context, encoding);
            if (location == null) {
                location = context.getLocation(document);
            }
            context.report(ISSUE, location, message);
            return;
        }

        // Even if no encoding is declared or UTF-8 is declared, check the actual
        // byte order mark / file bytes to see if a different encoding is being used
        File file = context.file;
        try {
            FileInputStream input = new FileInputStream(file);
            try {
                byte[] bom = new byte[4];
                int read = input.read(bom, 0, 4);
                if (read >= 2) {
                    // Check for non-UTF-8 BOM markers
                    // UTF-16 BE: FE FF
                    // UTF-16 LE: FF FE
                    // UTF-32 BE: 00 00 FE FF
                    // UTF-32 LE: FF FE 00 00
                    // UTF-8 BOM: EF BB BF (this is allowed)

                    if (read >= 4 &&
                            bom[0] == 0x00 && bom[1] == 0x00 &&
                            (bom[2] == (byte) 0xFE) && (bom[3] == (byte) 0xFF)) {
                        // UTF-32 BE
                        reportBomError(context, "UTF-32 BE");
                    } else if (read >= 4 &&
                            (bom[0] == (byte) 0xFF) && (bom[1] == (byte) 0xFE) &&
                            bom[2] == 0x00 && bom[3] == 0x00) {
                        // UTF-32 LE
                        reportBomError(context, "UTF-32 LE");
                    } else if ((bom[0] == (byte) 0xFE) && (bom[1] == (byte) 0xFF)) {
                        // UTF-16 BE
                        reportBomError(context, "UTF-16 BE");
                    } else if ((bom[0] == (byte) 0xFF) && (bom[1] == (byte) 0xFE)) {
                        // UTF-16 LE
                        reportBomError(context, "UTF-16 LE");
                    }
                    // EF BB BF is UTF-8 BOM - that's fine, don't report
                }
            } finally {
                input.close();
            }
        } catch (IOException e) {
            // Can't read the file: ignore
        }
    }

    private void reportBomError(@NonNull XmlContext context, @NonNull String detectedEncoding) {
        String message = String.format(
                "Resource files should be encoded in UTF-8 (detected encoding `%1$s` from BOM)",
                detectedEncoding);
        Location location = Location.create(context.file);
        context.report(ISSUE, location, message);
    }

    /**
     * Try to find the location of the encoding declaration in the XML prolog.
     */
    private Location guessEncodingLocation(@NonNull XmlContext context,
            @NonNull String encoding) {
        String contents = context.getContents();
        if (contents == null) {
            return null;
        }

        // Look for encoding="..." in the XML prolog
        int encodingIndex = contents.indexOf(encoding);
        if (encodingIndex == -1) {
            // Try case-insensitive search
            String lowerContents = contents.toLowerCase();
            String lowerEncoding = encoding.toLowerCase();
            encodingIndex = lowerContents.indexOf(lowerEncoding);
        }

        if (encodingIndex != -1) {
            return Location.create(context.file, contents, encodingIndex,
                    encodingIndex + encoding.length());
        }

        return null;
    }
}
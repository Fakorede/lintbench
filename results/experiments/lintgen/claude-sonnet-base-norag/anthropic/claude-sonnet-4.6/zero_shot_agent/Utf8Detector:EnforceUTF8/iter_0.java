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
        // Check the raw file bytes for an encoding declaration
        File file = context.file;
        String encoding = getXmlEncoding(file);

        if (encoding != null && !encoding.equalsIgnoreCase("utf-8")
                && !encoding.equalsIgnoreCase("utf8")) {
            // Found a non-UTF-8 encoding declaration
            Location location = getEncodingLocation(context, file, encoding);
            if (location == null) {
                location = Location.create(file);
            }
            context.report(ISSUE, location,
                    String.format("Resource file is not encoded in UTF-8; found encoding `%1$s`",
                            encoding));
        }
    }

    /**
     * Reads the raw bytes of the XML file and extracts the encoding attribute from the
     * XML declaration, if present.
     *
     * @param file the XML file to check
     * @return the encoding string if found, or null if no encoding declaration is present
     */
    private static String getXmlEncoding(File file) {
        try {
            byte[] bytes = new byte[200];
            InputStream is = new BufferedInputStream(new FileInputStream(file));
            try {
                int length = is.read(bytes);
                if (length < 5) {
                    return null;
                }
                // Look for <?xml ... encoding="..." ?>
                String header = new String(bytes, 0, length, "US-ASCII");
                if (!header.startsWith("<?xml")) {
                    return null;
                }
                int encodingIndex = header.indexOf("encoding");
                if (encodingIndex == -1) {
                    return null;
                }
                int start = encodingIndex + "encoding".length();
                // Skip whitespace and '='
                while (start < header.length() && (header.charAt(start) == ' '
                        || header.charAt(start) == '\t'
                        || header.charAt(start) == '=')) {
                    start++;
                }
                if (start >= header.length()) {
                    return null;
                }
                char quote = header.charAt(start);
                if (quote != '"' && quote != '\'') {
                    return null;
                }
                start++;
                int end = header.indexOf(quote, start);
                if (end == -1) {
                    return null;
                }
                return header.substring(start, end);
            } finally {
                is.close();
            }
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Attempts to find the exact location of the encoding declaration in the file.
     *
     * @param context the XML context
     * @param file    the XML file
     * @param encoding the encoding string that was found
     * @return a location pointing to the encoding declaration, or null
     */
    private static Location getEncodingLocation(XmlContext context, File file, String encoding) {
        try {
            byte[] bytes = new byte[200];
            InputStream is = new BufferedInputStream(new FileInputStream(file));
            try {
                int length = is.read(bytes);
                if (length < 5) {
                    return null;
                }
                String header = new String(bytes, 0, length, "US-ASCII");
                int encodingIndex = header.indexOf("encoding");
                if (encodingIndex == -1) {
                    return null;
                }
                // Find the start of the encoding value (after the quote)
                int start = encodingIndex + "encoding".length();
                while (start < header.length() && (header.charAt(start) == ' '
                        || header.charAt(start) == '\t'
                        || header.charAt(start) == '=')) {
                    start++;
                }
                if (start >= header.length()) {
                    return null;
                }
                char quote = header.charAt(start);
                if (quote != '"' && quote != '\'') {
                    return null;
                }
                // start points to the opening quote; the value starts at start+1
                int valueStart = start + 1;
                int valueEnd = valueStart + encoding.length();

                Location startLocation = Location.create(file,
                        new com.android.tools.lint.detector.api.DefaultPosition(0, valueStart, valueStart),
                        new com.android.tools.lint.detector.api.DefaultPosition(0, valueEnd, valueEnd));
                return startLocation;
            } finally {
                is.close();
            }
        } catch (IOException e) {
            return null;
        }
    }
}
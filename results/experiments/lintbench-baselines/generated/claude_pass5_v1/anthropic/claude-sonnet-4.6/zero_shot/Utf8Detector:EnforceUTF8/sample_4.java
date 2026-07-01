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
            Category.CORRECTNESS,
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
        // Check the encoding of the file by reading the raw bytes and looking for
        // an XML declaration with an encoding attribute.
        File file = context.file;
        String encoding = getEncoding(file);

        if (encoding != null) {
            // There is an explicit encoding declaration; check if it's UTF-8
            if (!encoding.equalsIgnoreCase("utf-8")) {
                // Report the issue
                Location location = Location.create(file);
                context.report(
                        ISSUE,
                        location,
                        String.format(
                                "Not using UTF-8 encoding; found `encoding=%1$s` but expected `encoding=utf-8`",
                                encoding));
            }
        }
        // If there's no encoding declaration, XML defaults to UTF-8, which is fine.
    }

    /**
     * Reads the beginning of the given file and extracts the encoding from the
     * XML declaration, if present. Returns null if no encoding is specified.
     */
    private static String getEncoding(@NonNull File file) {
        try {
            InputStream input = new BufferedInputStream(new FileInputStream(file));
            try {
                // Read enough bytes to find the XML declaration
                byte[] buf = new byte[512];
                int count = 0;
                int b;
                while (count < buf.length) {
                    int read = input.read(buf, count, buf.length - count);
                    if (read == -1) {
                        break;
                    }
                    count += read;
                }

                // Convert to string - use ASCII/Latin-1 to preserve byte values
                String declaration = new String(buf, 0, count, "US-ASCII");

                // Look for the XML declaration: <?xml ... encoding="..." ... ?>
                if (!declaration.startsWith("<?xml")) {
                    return null;
                }

                // Find the end of the XML declaration
                int end = declaration.indexOf("?>");
                if (end == -1) {
                    end = declaration.length();
                }
                String xmlDecl = declaration.substring(0, end);

                // Look for encoding attribute
                return extractEncoding(xmlDecl);
            } finally {
                try {
                    input.close();
                } catch (IOException ignore) {
                }
            }
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Extracts the encoding value from an XML declaration string.
     * Returns null if no encoding attribute is found.
     */
    private static String extractEncoding(@NonNull String xmlDecl) {
        // Look for encoding= in the declaration
        int index = xmlDecl.indexOf("encoding");
        if (index == -1) {
            return null;
        }

        index += "encoding".length();

        // Skip whitespace
        while (index < xmlDecl.length() && Character.isWhitespace(xmlDecl.charAt(index))) {
            index++;
        }

        // Expect '='
        if (index >= xmlDecl.length() || xmlDecl.charAt(index) != '=') {
            return null;
        }
        index++;

        // Skip whitespace
        while (index < xmlDecl.length() && Character.isWhitespace(xmlDecl.charAt(index))) {
            index++;
        }

        if (index >= xmlDecl.length()) {
            return null;
        }

        // Get the quote character
        char quote = xmlDecl.charAt(index);
        if (quote != '"' && quote != '\'') {
            return null;
        }
        index++;

        // Find the closing quote
        int endQuote = xmlDecl.indexOf(quote, index);
        if (endQuote == -1) {
            return null;
        }

        return xmlDecl.substring(index, endQuote);
    }
}
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
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        // Check the raw bytes of the file to see if there's an encoding declaration
        // that is not UTF-8.
        File file = context.file;
        String encoding = getEncoding(file);

        if (encoding != null) {
            // There's an explicit encoding declaration
            if (!encoding.equalsIgnoreCase("utf-8") && !encoding.equalsIgnoreCase("utf8")) {
                Location location = Location.create(file);
                String message = String.format(
                        "Resource files should be encoded in UTF-8 (found encoding `%1$s`)",
                        encoding);
                context.report(ISSUE, location, message);
            }
        }
        // If no encoding is declared, XML defaults to UTF-8, which is fine.
    }

    /**
     * Returns the encoding declared in the XML prologue of the given file, or null if
     * no encoding is declared.
     */
    private static String getEncoding(File file) {
        try {
            InputStream input = new BufferedInputStream(new FileInputStream(file));
            try {
                // Read enough bytes to find an encoding declaration
                byte[] buf = new byte[1024];
                int count = 0;
                int read;
                while (count < buf.length) {
                    read = input.read(buf, count, buf.length - count);
                    if (read == -1) {
                        break;
                    }
                    count += read;
                }

                // Convert to string (using ASCII which is safe for the prologue)
                String prologue = new String(buf, 0, count, "US-ASCII");

                // Look for the XML declaration
                // <?xml version="1.0" encoding="..."?>
                int encodingIndex = prologue.indexOf("encoding");
                if (encodingIndex == -1) {
                    return null;
                }

                // Make sure this is in the XML prologue (before the root element)
                int prologueEnd = prologue.indexOf("?>");
                if (prologueEnd != -1 && encodingIndex > prologueEnd) {
                    return null;
                }

                // Find the encoding value
                int equalsIndex = prologue.indexOf('=', encodingIndex + 8);
                if (equalsIndex == -1) {
                    return null;
                }

                // Skip whitespace after '='
                int i = equalsIndex + 1;
                while (i < prologue.length() && Character.isWhitespace(prologue.charAt(i))) {
                    i++;
                }

                if (i >= prologue.length()) {
                    return null;
                }

                // Get the quote character
                char quote = prologue.charAt(i);
                if (quote != '"' && quote != '\'') {
                    return null;
                }

                int start = i + 1;
                int end = prologue.indexOf(quote, start);
                if (end == -1) {
                    return null;
                }

                return prologue.substring(start, end);
            } finally {
                try {
                    input.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        } catch (IOException e) {
            return null;
        }
    }
}
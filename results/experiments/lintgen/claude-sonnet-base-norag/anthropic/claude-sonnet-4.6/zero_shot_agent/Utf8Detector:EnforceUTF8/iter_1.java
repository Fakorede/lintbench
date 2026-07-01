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
import com.android.tools.lint.detector.api.DefaultPosition;
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
        File file = context.file;

        // First check for BOM or encoding that indicates non-UTF-8
        EncodingResult result = detectEncoding(file);

        if (result != null) {
            String encoding = result.encoding;
            if (encoding != null && !encoding.equalsIgnoreCase("utf-8")
                    && !encoding.equalsIgnoreCase("utf8")) {
                Location location = result.location;
                if (location == null) {
                    location = Location.create(file);
                }
                context.report(ISSUE, location,
                        String.format(
                                "Resource file is not encoded in UTF-8; found encoding `%1$s`",
                                encoding));
            }
        }
    }

    private static class EncodingResult {
        String encoding;
        Location location;

        EncodingResult(String encoding, Location location) {
            this.encoding = encoding;
            this.location = location;
        }
    }

    /**
     * Detects the encoding of an XML file by examining its byte order mark (BOM)
     * and/or XML declaration.
     */
    private static EncodingResult detectEncoding(File file) {
        try {
            byte[] bytes = new byte[400];
            InputStream is = new BufferedInputStream(new FileInputStream(file));
            try {
                int length = is.read(bytes);
                if (length < 2) {
                    return null;
                }

                // Check for BOM
                // UTF-32 BE BOM: 00 00 FE FF
                // UTF-32 LE BOM: FF FE 00 00
                // UTF-16 BE BOM: FE FF
                // UTF-16 LE BOM: FF FE
                // UTF-8 BOM: EF BB BF

                if (length >= 4) {
                    int b0 = bytes[0] & 0xFF;
                    int b1 = bytes[1] & 0xFF;
                    int b2 = bytes[2] & 0xFF;
                    int b3 = bytes[3] & 0xFF;

                    // UTF-32 BE BOM
                    if (b0 == 0x00 && b1 == 0x00 && b2 == 0xFE && b3 == 0xFF) {
                        return new EncodingResult("UTF-32", Location.create(file));
                    }
                    // UTF-32 LE BOM
                    if (b0 == 0xFF && b1 == 0xFE && b2 == 0x00 && b3 == 0x00) {
                        return new EncodingResult("UTF-32", Location.create(file));
                    }

                    // Check for UTF-32 without BOM by looking at null byte patterns
                    // UTF-32 BE without BOM: 00 00 00 3C (<?xml starts with <)
                    if (b0 == 0x00 && b1 == 0x00 && b2 == 0x00 && b3 == 0x3C) {
                        return new EncodingResult("UTF-32", Location.create(file));
                    }
                    // UTF-32 LE without BOM: 3C 00 00 00
                    if (b0 == 0x3C && b1 == 0x00 && b2 == 0x00 && b3 == 0x00) {
                        return new EncodingResult("UTF-32", Location.create(file));
                    }
                    // UTF-32 BE without BOM (2143): 00 00 3C 00
                    if (b0 == 0x00 && b1 == 0x00 && b2 == 0x3C && b3 == 0x00) {
                        return new EncodingResult("UTF-32", Location.create(file));
                    }
                    // UTF-32 LE without BOM (3412): 00 3C 00 00
                    if (b0 == 0x00 && b1 == 0x3C && b2 == 0x00 && b3 == 0x00) {
                        return new EncodingResult("UTF-32", Location.create(file));
                    }
                }

                if (length >= 2) {
                    int b0 = bytes[0] & 0xFF;
                    int b1 = bytes[1] & 0xFF;

                    // UTF-16 BE BOM
                    if (b0 == 0xFE && b1 == 0xFF) {
                        // Has BOM, check for encoding declaration in UTF-16 BE
                        String encoding = extractEncodingFromUtf16BE(bytes, length);
                        if (encoding != null && !encoding.equalsIgnoreCase("utf-8")
                                && !encoding.equalsIgnoreCase("utf8")) {
                            return new EncodingResult(encoding, Location.create(file));
                        }
                        return new EncodingResult("UTF-16", Location.create(file));
                    }
                    // UTF-16 LE BOM
                    if (b0 == 0xFF && b1 == 0xFE) {
                        // Has BOM, check for encoding declaration in UTF-16 LE
                        String encoding = extractEncodingFromUtf16LE(bytes, length);
                        if (encoding != null && !encoding.equalsIgnoreCase("utf-8")
                                && !encoding.equalsIgnoreCase("utf8")) {
                            return new EncodingResult(encoding, Location.create(file));
                        }
                        return new EncodingResult("UTF-16", Location.create(file));
                    }

                    // UTF-16 BE without BOM: 00 3C 00 3F (<?xml)
                    if (length >= 4) {
                        int b2 = bytes[2] & 0xFF;
                        int b3 = bytes[3] & 0xFF;
                        if (b0 == 0x00 && b1 == 0x3C && b2 == 0x00 && b3 == 0x3F) {
                            String encoding = extractEncodingFromUtf16BE(bytes, length);
                            if (encoding != null) {
                                return new EncodingResult(encoding, Location.create(file));
                            }
                            return new EncodingResult("UTF-16", Location.create(file));
                        }
                        // UTF-16 LE without BOM: 3C 00 3F 00 (<?xml)
                        if (b0 == 0x3C && b1 == 0x00 && b2 == 0x3F && b3 == 0x00) {
                            String encoding = extractEncodingFromUtf16LE(bytes, length);
                            if (encoding != null) {
                                return new EncodingResult(encoding, Location.create(file));
                            }
                            return new EncodingResult("UTF-16", Location.create(file));
                        }
                    }
                }

                // UTF-8 BOM: EF BB BF
                if (length >= 3) {
                    int b0 = bytes[0] & 0xFF;
                    int b1 = bytes[1] & 0xFF;
                    int b2 = bytes[2] & 0xFF;
                    if (b0 == 0xEF && b1 == 0xBB && b2 == 0xBF) {
                        // UTF-8 with BOM - this is fine, it's still UTF-8
                        // But check if there's an explicit encoding declaration that says otherwise
                        // Skip the BOM and look for encoding declaration
                        String header = new String(bytes, 3, length - 3, "US-ASCII");
                        String encoding = extractEncodingFromAsciiHeader(header);
                        if (encoding != null && !encoding.equalsIgnoreCase("utf-8")
                                && !encoding.equalsIgnoreCase("utf8")) {
                            return new EncodingResult(encoding,
                                    getEncodingLocation(file, bytes, length, encoding, 3));
                        }
                        return null; // UTF-8 with BOM is acceptable
                    }
                }

                // Regular ASCII/UTF-8 - check for encoding declaration
                String header = new String(bytes, 0, length, "US-ASCII");
                if (!header.startsWith("<?xml")) {
                    return null;
                }

                String encoding = extractEncodingFromAsciiHeader(header);
                if (encoding == null) {
                    return null;
                }

                if (!encoding.equalsIgnoreCase("utf-8") && !encoding.equalsIgnoreCase("utf8")) {
                    Location location = getEncodingLocation(file, bytes, length, encoding, 0);
                    if (location == null) {
                        location = Location.create(file);
                    }
                    return new EncodingResult(encoding, location);
                }

                return null;
            } finally {
                is.close();
            }
        } catch (IOException e) {
            return null;
        }
    }

    private static String extractEncodingFromAsciiHeader(String header) {
        int encodingIndex = header.indexOf("encoding");
        if (encodingIndex == -1) {
            return null;
        }
        int start = encodingIndex + "encoding".length();
        // Skip whitespace and '='
        while (start < header.length()) {
            char c = header.charAt(start);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                start++;
            } else if (c == '=') {
                start++;
                break;
            } else {
                break;
            }
        }
        // Skip whitespace after '='
        while (start < header.length()) {
            char c = header.charAt(start);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                start++;
            } else {
                break;
            }
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
    }

    private static String extractEncodingFromUtf16BE(byte[] bytes, int length) {
        // Convert UTF-16 BE bytes to a string, skipping BOM if present
        try {
            int start = 0;
            if (length >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
                start = 2; // Skip BOM
            }
            String header = new String(bytes, start, length - start, "UTF-16BE");
            return extractEncodingFromAsciiHeader(header);
        } catch (Exception e) {
            return null;
        }
    }

    private static String extractEncodingFromUtf16LE(byte[] bytes, int length) {
        // Convert UTF-16 LE bytes to a string, skipping BOM if present
        try {
            int start = 0;
            if (length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
                start = 2; // Skip BOM
            }
            String header = new String(bytes, start, length - start, "UTF-16LE");
            return extractEncodingFromAsciiHeader(header);
        } catch (Exception e) {
            return null;
        }
    }

    private static Location getEncodingLocation(File file, byte[] bytes, int length,
            String encoding, int headerOffset) {
        try {
            String header = new String(bytes, headerOffset, length - headerOffset, "US-ASCII");
            int encodingIndex = header.indexOf("encoding");
            if (encodingIndex == -1) {
                return null;
            }
            int start = encodingIndex + "encoding".length();
            while (start < header.length()) {
                char c = header.charAt(start);
                if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                    start++;
                } else if (c == '=') {
                    start++;
                    break;
                } else {
                    break;
                }
            }
            while (start < header.length()) {
                char c = header.charAt(start);
                if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                    start++;
                } else {
                    break;
                }
            }
            if (start >= header.length()) {
                return null;
            }
            char quote = header.charAt(start);
            if (quote != '"' && quote != '\'') {
                return null;
            }
            int valueStart = start + 1 + headerOffset;
            int valueEnd = valueStart + encoding.length();

            return Location.create(file,
                    new DefaultPosition(0, valueStart, valueStart),
                    new DefaultPosition(0, valueEnd, valueEnd));
        } catch (Exception e) {
            return null;
        }
    }
}
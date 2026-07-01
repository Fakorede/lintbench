package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.OtherFileScanner;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Document;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;

public class Utf8Detector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "EnforceUTF8",
            "Encoding used in resource files is not UTF-8",
            "XML supports encoding in a wide variety of character sets. However, not all " +
            "tools handle the XML encoding attribute correctly, and nearly all Android " +
            "apps use UTF-8, so by using UTF-8 you can protect yourself against subtle " +
            "bugs when using non-ASCII characters.\n\n" +
            "In particular, the Android Gradle build system will merge resource XML files " +
            "assuming the resource files are using UTF-8 encoding.",
            Category.I18N,
            8,
            Severity.ERROR,
            new Implementation(
                    Utf8Detector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    public Utf8Detector() {
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        // Read the raw bytes of the file to check the XML declaration encoding
        File file = context.file;
        String encoding = getXmlEncoding(file);

        if (encoding != null && !encoding.equalsIgnoreCase("utf-8")
                && !encoding.equalsIgnoreCase("utf8")) {
            String message = String.format(
                    "Resource files should be encoded in UTF-8 (found encoding `%1$s`)",
                    encoding);
            Location location = Location.create(file);
            context.report(ISSUE, document, location, message);
        } else if (encoding == null) {
            // Check if there's a BOM or other indicator; if no declaration, UTF-8 is default
            // Also check for non-UTF-8 BOMs
            String bom = detectBom(file);
            if (bom != null && !bom.equalsIgnoreCase("utf-8")) {
                String message = String.format(
                        "Resource files should be encoded in UTF-8 (found BOM indicating `%1$s`)",
                        bom);
                Location location = Location.create(file);
                context.report(ISSUE, document, location, message);
            }
        }
    }

    /**
     * Reads the raw bytes of the XML file and extracts the encoding from the XML declaration,
     * e.g. {@code <?xml version="1.0" encoding="ISO-8859-1"?>}.
     *
     * @param file the XML file to inspect
     * @return the encoding string if found, or {@code null} if no encoding attribute is present
     */
    private static String getXmlEncoding(File file) {
        try {
            byte[] bytes = readFirstBytes(file, 200);
            if (bytes == null) {
                return null;
            }

            // Convert to ASCII string for parsing the XML declaration
            String header = new String(bytes, "ISO-8859-1");

            if (!header.startsWith("<?xml")) {
                return null;
            }

            int encodingIndex = header.indexOf("encoding");
            if (encodingIndex == -1) {
                return null;
            }

            int eqIndex = header.indexOf('=', encodingIndex);
            if (eqIndex == -1) {
                return null;
            }

            // Skip whitespace after '='
            int i = eqIndex + 1;
            while (i < header.length() && Character.isWhitespace(header.charAt(i))) {
                i++;
            }

            if (i >= header.length()) {
                return null;
            }

            char quote = header.charAt(i);
            if (quote != '"' && quote != '\'') {
                return null;
            }

            int start = i + 1;
            int end = header.indexOf(quote, start);
            if (end == -1) {
                return null;
            }

            return header.substring(start, end);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Detects a BOM at the start of the file and returns the corresponding encoding name,
     * or {@code null} if no BOM is found.
     */
    private static String detectBom(File file) {
        try {
            byte[] bytes = readFirstBytes(file, 4);
            if (bytes == null || bytes.length < 2) {
                return null;
            }

            // UTF-8 BOM: EF BB BF
            if (bytes.length >= 3
                    && (bytes[0] & 0xFF) == 0xEF
                    && (bytes[1] & 0xFF) == 0xBB
                    && (bytes[2] & 0xFF) == 0xBF) {
                return "utf-8";
            }

            // UTF-32 BE BOM: 00 00 FE FF
            if (bytes.length >= 4
                    && (bytes[0] & 0xFF) == 0x00
                    && (bytes[1] & 0xFF) == 0x00
                    && (bytes[2] & 0xFF) == 0xFE
                    && (bytes[3] & 0xFF) == 0xFF) {
                return "UTF-32BE";
            }

            // UTF-32 LE BOM: FF FE 00 00
            if (bytes.length >= 4
                    && (bytes[0] & 0xFF) == 0xFF
                    && (bytes[1] & 0xFF) == 0xFE
                    && (bytes[2] & 0xFF) == 0x00
                    && (bytes[3] & 0xFF) == 0x00) {
                return "UTF-32LE";
            }

            // UTF-16 BE BOM: FE FF
            if ((bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
                return "UTF-16BE";
            }

            // UTF-16 LE BOM: FF FE
            if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
                return "UTF-16LE";
            }

            return null;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Reads up to {@code maxBytes} bytes from the beginning of the given file.
     */
    private static byte[] readFirstBytes(File file, int maxBytes) throws IOException {
        InputStream is = null;
        try {
            is = new BufferedInputStream(new FileInputStream(file));
            byte[] buffer = new byte[maxBytes];
            int read = 0;
            int n;
            while (read < maxBytes && (n = is.read(buffer, read, maxBytes - read)) != -1) {
                read += n;
            }
            if (read == 0) {
                return null;
            }
            if (read < maxBytes) {
                byte[] trimmed = new byte[read];
                System.arraycopy(buffer, 0, trimmed, 0, read);
                return trimmed;
            }
            return buffer;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}
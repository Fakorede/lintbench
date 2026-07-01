package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
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
        File file = context.file;

        // First check for BOM-based encoding
        String bomEncoding = getBomEncoding(file);
        if (bomEncoding != null) {
            if (!isUtf8(bomEncoding)) {
                String message = String.format(
                        "Resource file is not encoded in UTF-8; found encoding `%1$s`",
                        bomEncoding);
                Location location = Location.create(file);
                context.report(ISSUE, document.getDocumentElement(), location, message);
            }
            return;
        }

        // Check the XML declaration encoding attribute
        String declaredEncoding = getXmlDeclaredEncoding(file);
        if (declaredEncoding != null && !isUtf8(declaredEncoding)) {
            String message = String.format(
                    "Resource file is not encoded in UTF-8; found encoding `%1$s`",
                    declaredEncoding);
            Location location = Location.create(file);
            context.report(ISSUE, document.getDocumentElement(), location, message);
            return;
        }

        // Check for UTF-32 without BOM by looking at null byte patterns
        String detectedEncoding = detectEncodingFromNullBytes(file);
        if (detectedEncoding != null && !isUtf8(detectedEncoding)) {
            String message = String.format(
                    "Resource file is not encoded in UTF-8; found encoding `%1$s`",
                    detectedEncoding);
            Location location = Location.create(file);
            context.report(ISSUE, document.getDocumentElement(), location, message);
        }
    }

    private static boolean isUtf8(String encoding) {
        return encoding.equalsIgnoreCase("utf-8") || encoding.equalsIgnoreCase("utf8");
    }

    /**
     * Detects encoding based on BOM bytes at the start of the file.
     * Returns null if no BOM is found.
     */
    private static String getBomEncoding(File file) {
        try {
            byte[] bytes = readFirstBytes(file, 4);
            if (bytes == null || bytes.length < 2) {
                return null;
            }

            int b0 = bytes[0] & 0xFF;
            int b1 = bytes[1] & 0xFF;

            if (bytes.length >= 4) {
                int b2 = bytes[2] & 0xFF;
                int b3 = bytes[3] & 0xFF;

                // UTF-32 BE BOM: 00 00 FE FF
                if (b0 == 0x00 && b1 == 0x00 && b2 == 0xFE && b3 == 0xFF) {
                    return "UTF-32BE";
                }
                // UTF-32 LE BOM: FF FE 00 00
                if (b0 == 0xFF && b1 == 0xFE && b2 == 0x00 && b3 == 0x00) {
                    return "UTF-32LE";
                }
            }

            if (bytes.length >= 3) {
                int b2 = bytes[2] & 0xFF;
                // UTF-8 BOM: EF BB BF
                if (b0 == 0xEF && b1 == 0xBB && b2 == 0xBF) {
                    return "UTF-8"; // UTF-8 BOM is acceptable
                }
            }

            // UTF-16 BE BOM: FE FF
            if (b0 == 0xFE && b1 == 0xFF) {
                return "UTF-16BE";
            }
            // UTF-16 LE BOM: FF FE
            if (b0 == 0xFF && b1 == 0xFE) {
                return "UTF-16LE";
            }

            return null;

        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Detects UTF-16 or UTF-32 encoding without BOM by looking at null byte patterns
     * in the first few bytes of the file (which should contain the '<' character of
     * the XML declaration or document element).
     */
    private static String detectEncodingFromNullBytes(File file) {
        try {
            byte[] bytes = readFirstBytes(file, 4);
            if (bytes == null || bytes.length < 4) {
                return null;
            }

            int b0 = bytes[0] & 0xFF;
            int b1 = bytes[1] & 0xFF;
            int b2 = bytes[2] & 0xFF;
            int b3 = bytes[3] & 0xFF;

            // UTF-32 BE without BOM: 00 00 00 3C (for '<')
            if (b0 == 0x00 && b1 == 0x00 && b2 == 0x00 && b3 == 0x3C) {
                return "UTF-32BE";
            }
            // UTF-32 LE without BOM: 3C 00 00 00 (for '<')
            if (b0 == 0x3C && b1 == 0x00 && b2 == 0x00 && b3 == 0x00) {
                return "UTF-32LE";
            }
            // UTF-16 BE without BOM: 00 3C 00 3F (for '<?')
            if (b0 == 0x00 && b1 == 0x3C && b2 == 0x00 && b3 == 0x3F) {
                return "UTF-16BE";
            }
            // UTF-16 LE without BOM: 3C 00 3F 00 (for '<?')
            if (b0 == 0x3C && b1 == 0x00 && b2 == 0x3F && b3 == 0x00) {
                return "UTF-16LE";
            }

            return null;

        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Reads the XML declaration from the file and extracts the encoding attribute value.
     * Returns null if no encoding is declared.
     */
    private static String getXmlDeclaredEncoding(File file) {
        try {
            byte[] bytes = readFirstBytes(file, 200);
            if (bytes == null) {
                return null;
            }

            // Convert to ASCII string for parsing the XML declaration
            String header = new String(bytes, "ISO-8859-1");

            // Skip UTF-8 BOM if present
            int start = 0;
            if (bytes.length >= 3 &&
                    (bytes[0] & 0xFF) == 0xEF &&
                    (bytes[1] & 0xFF) == 0xBB &&
                    (bytes[2] & 0xFF) == 0xBF) {
                start = 3;
            }

            if (start >= header.length()) {
                return null;
            }

            header = header.substring(start);

            if (!header.startsWith("<?xml")) {
                return null;
            }

            // Find the end of the XML declaration
            int end = header.indexOf("?>");
            if (end == -1) {
                return null;
            }

            String declaration = header.substring(0, end + 2);

            // Look for encoding attribute
            int encodingIndex = declaration.indexOf("encoding");
            if (encodingIndex == -1) {
                return null;
            }

            // Find the value
            int eqIndex = declaration.indexOf('=', encodingIndex);
            if (eqIndex == -1) {
                return null;
            }

            // Skip whitespace after '='
            int valueStart = eqIndex + 1;
            while (valueStart < declaration.length() && Character.isWhitespace(declaration.charAt(valueStart))) {
                valueStart++;
            }

            if (valueStart >= declaration.length()) {
                return null;
            }

            char quote = declaration.charAt(valueStart);
            if (quote != '\'' && quote != '"') {
                return null;
            }

            int valueEnd = declaration.indexOf(quote, valueStart + 1);
            if (valueEnd == -1) {
                return null;
            }

            return declaration.substring(valueStart + 1, valueEnd);

        } catch (IOException e) {
            return null;
        }
    }

    private static byte[] readFirstBytes(File file, int count) throws IOException {
        InputStream is = null;
        try {
            is = new BufferedInputStream(new FileInputStream(file));
            byte[] buffer = new byte[count];
            int total = 0;
            int read;
            while (total < count && (read = is.read(buffer, total, count - total)) != -1) {
                total += read;
            }
            if (total == 0) {
                return null;
            }
            if (total < count) {
                byte[] trimmed = new byte[total];
                System.arraycopy(buffer, 0, trimmed, 0, total);
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
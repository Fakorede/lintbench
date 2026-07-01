package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
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
import java.util.Arrays;

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
        
        // First check BOM encoding
        String bomEncoding = getBomEncoding(file);
        
        // Then check declared encoding in XML declaration
        String declaredEncoding = getXmlDeclaredEncoding(file, bomEncoding);
        
        if (declaredEncoding != null) {
            // An explicit encoding was declared; check if it's UTF-8
            if (!isUtf8(declaredEncoding)) {
                String message = String.format(
                        "Resource file is not encoded in UTF-8; found encoding `%1$s`",
                        declaredEncoding);
                Location location = Location.create(file);
                context.report(ISSUE, location, message);
            }
        } else if (bomEncoding != null) {
            // No encoding declared but BOM present
            if (!isUtf8(bomEncoding)) {
                String message = String.format(
                        "Resource file is not encoded in UTF-8; found encoding `%1$s` from BOM",
                        bomEncoding);
                Location location = Location.create(file);
                context.report(ISSUE, location, message);
            }
        } else {
            // No BOM, no declared encoding - check if the file appears to be UTF-32 or UTF-16
            // by examining the raw bytes pattern
            String detectedEncoding = detectEncodingFromBytes(file);
            if (detectedEncoding != null && !isUtf8(detectedEncoding)) {
                String message = String.format(
                        "Resource file is not encoded in UTF-8; found encoding `%1$s`",
                        detectedEncoding);
                Location location = Location.create(file);
                context.report(ISSUE, location, message);
            }
        }
    }

    private static boolean isUtf8(String encoding) {
        return encoding.equalsIgnoreCase("utf-8") || encoding.equalsIgnoreCase("utf8");
    }

    /**
     * Detects encoding from the byte pattern of the file (for files without BOM).
     * UTF-32 BE without BOM starts with 00 00 00 3C (for '<')
     * UTF-32 LE without BOM starts with 3C 00 00 00 (for '<')
     * UTF-16 BE without BOM starts with 00 3C (for '<')
     * UTF-16 LE without BOM starts with 3C 00 (for '<')
     */
    private static String detectEncodingFromBytes(File file) {
        try {
            byte[] bytes = readBytes(file, 4);
            if (bytes == null || bytes.length < 2) {
                return null;
            }

            int b0 = bytes[0] & 0xFF;
            int b1 = bytes[1] & 0xFF;

            if (bytes.length >= 4) {
                int b2 = bytes[2] & 0xFF;
                int b3 = bytes[3] & 0xFF;
                
                // UTF-32 BE without BOM: 00 00 00 3C
                if (b0 == 0x00 && b1 == 0x00 && b2 == 0x00 && b3 == 0x3C) {
                    return "UTF-32BE";
                }
                // UTF-32 LE without BOM: 3C 00 00 00
                if (b0 == 0x3C && b1 == 0x00 && b2 == 0x00 && b3 == 0x00) {
                    return "UTF-32LE";
                }
            }

            // UTF-16 BE without BOM: 00 3C
            if (b0 == 0x00 && b1 == 0x3C) {
                return "UTF-16BE";
            }
            // UTF-16 LE without BOM: 3C 00
            if (b0 == 0x3C && b1 == 0x00) {
                return "UTF-16LE";
            }

            return null;

        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Reads the beginning of the XML file and extracts the encoding from the XML declaration.
     * Handles various encodings including UTF-16 and UTF-32.
     */
    private static String getXmlDeclaredEncoding(File file, String bomEncoding) {
        try {
            byte[] bytes = readBytes(file, 400);
            if (bytes == null) {
                return null;
            }

            String header = null;
            int offset = 0;

            // Determine how to read the header based on BOM or byte patterns
            if (bomEncoding != null) {
                if (bomEncoding.equalsIgnoreCase("UTF-8")) {
                    offset = 3; // Skip UTF-8 BOM (EF BB BF)
                    header = extractAsciiFromUtf8(bytes, offset);
                } else if (bomEncoding.equalsIgnoreCase("UTF-16BE")) {
                    offset = 2; // Skip UTF-16 BE BOM (FE FF)
                    header = extractAsciiFromUtf16Be(bytes, offset);
                } else if (bomEncoding.equalsIgnoreCase("UTF-16LE")) {
                    offset = 2; // Skip UTF-16 LE BOM (FF FE)
                    // Check if it's actually UTF-32 LE (FF FE 00 00)
                    if (bytes.length >= 4 && (bytes[2] & 0xFF) == 0x00 && (bytes[3] & 0xFF) == 0x00) {
                        offset = 4;
                        header = extractAsciiFromUtf32Le(bytes, offset);
                    } else {
                        header = extractAsciiFromUtf16Le(bytes, offset);
                    }
                } else if (bomEncoding.equalsIgnoreCase("UTF-32BE")) {
                    offset = 4; // Skip UTF-32 BE BOM
                    header = extractAsciiFromUtf32Be(bytes, offset);
                } else if (bomEncoding.equalsIgnoreCase("UTF-32LE")) {
                    offset = 4; // Skip UTF-32 LE BOM
                    header = extractAsciiFromUtf32Le(bytes, offset);
                }
            } else {
                // No BOM - detect from byte patterns
                if (bytes.length >= 4) {
                    int b0 = bytes[0] & 0xFF;
                    int b1 = bytes[1] & 0xFF;
                    int b2 = bytes[2] & 0xFF;
                    int b3 = bytes[3] & 0xFF;
                    
                    if (b0 == 0x00 && b1 == 0x00 && b2 == 0x00 && b3 == 0x3C) {
                        // UTF-32 BE without BOM
                        header = extractAsciiFromUtf32Be(bytes, 0);
                    } else if (b0 == 0x3C && b1 == 0x00 && b2 == 0x00 && b3 == 0x00) {
                        // UTF-32 LE without BOM
                        header = extractAsciiFromUtf32Le(bytes, 0);
                    } else if (b0 == 0x00 && b1 == 0x3C) {
                        // UTF-16 BE without BOM
                        header = extractAsciiFromUtf16Be(bytes, 0);
                    } else if (b0 == 0x3C && b1 == 0x00) {
                        // UTF-16 LE without BOM
                        header = extractAsciiFromUtf16Le(bytes, 0);
                    } else {
                        // Assume UTF-8
                        header = new String(bytes, "US-ASCII");
                    }
                } else {
                    header = new String(bytes, "US-ASCII");
                }
            }

            if (header == null) {
                return null;
            }

            return parseEncodingFromDeclaration(header);

        } catch (IOException e) {
            return null;
        }
    }

    private static String extractAsciiFromUtf8(byte[] bytes, int offset) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = offset; i < bytes.length; i++) {
            sb.append((char)(bytes[i] & 0xFF));
        }
        return sb.toString();
    }

    private static String extractAsciiFromUtf16Be(byte[] bytes, int offset) {
        StringBuilder sb = new StringBuilder();
        for (int i = offset + 1; i < bytes.length; i += 2) {
            sb.append((char)(bytes[i] & 0xFF));
        }
        return sb.toString();
    }

    private static String extractAsciiFromUtf16Le(byte[] bytes, int offset) {
        StringBuilder sb = new StringBuilder();
        for (int i = offset; i + 1 < bytes.length; i += 2) {
            sb.append((char)(bytes[i] & 0xFF));
        }
        return sb.toString();
    }

    private static String extractAsciiFromUtf32Be(byte[] bytes, int offset) {
        StringBuilder sb = new StringBuilder();
        for (int i = offset + 3; i < bytes.length; i += 4) {
            sb.append((char)(bytes[i] & 0xFF));
        }
        return sb.toString();
    }

    private static String extractAsciiFromUtf32Le(byte[] bytes, int offset) {
        StringBuilder sb = new StringBuilder();
        for (int i = offset; i + 3 < bytes.length; i += 4) {
            sb.append((char)(bytes[i] & 0xFF));
        }
        return sb.toString();
    }

    private static String parseEncodingFromDeclaration(String header) {
        if (!header.startsWith("<?xml")) {
            return null;
        }

        int encodingIndex = header.indexOf("encoding");
        if (encodingIndex == -1) {
            return null;
        }

        // Make sure we're within the XML declaration
        int declarationEnd = header.indexOf("?>");
        if (declarationEnd != -1 && encodingIndex > declarationEnd) {
            return null;
        }

        int pos = encodingIndex + "encoding".length();

        // Skip whitespace
        while (pos < header.length() && Character.isWhitespace(header.charAt(pos))) {
            pos++;
        }

        // Expect '='
        if (pos >= header.length() || header.charAt(pos) != '=') {
            return null;
        }
        pos++;

        // Skip whitespace
        while (pos < header.length() && Character.isWhitespace(header.charAt(pos))) {
            pos++;
        }

        if (pos >= header.length()) {
            return null;
        }

        // Get the quote character
        char quote = header.charAt(pos);
        if (quote != '"' && quote != '\'') {
            return null;
        }
        pos++;

        // Find the closing quote
        int end = header.indexOf(quote, pos);
        if (end == -1) {
            return null;
        }

        return header.substring(pos, end);
    }

    /**
     * Checks the BOM (Byte Order Mark) of the file to determine encoding.
     */
    private static String getBomEncoding(File file) {
        try {
            byte[] bytes = readBytes(file, 4);
            if (bytes == null || bytes.length < 2) {
                return null;
            }

            int b0 = bytes[0] & 0xFF;
            int b1 = bytes[1] & 0xFF;

            if (bytes.length >= 3) {
                int b2 = bytes[2] & 0xFF;
                if (b0 == 0xEF && b1 == 0xBB && b2 == 0xBF) {
                    return "UTF-8";
                }
            }

            if (bytes.length >= 4) {
                int b2 = bytes[2] & 0xFF;
                int b3 = bytes[3] & 0xFF;
                if (b0 == 0x00 && b1 == 0x00 && b2 == 0xFE && b3 == 0xFF) {
                    return "UTF-32BE";
                }
                // UTF-32 LE BOM is FF FE 00 00 (must check before UTF-16 LE)
                if (b0 == 0xFF && b1 == 0xFE && b2 == 0x00 && b3 == 0x00) {
                    return "UTF-32LE";
                }
            }

            if (b0 == 0xFE && b1 == 0xFF) {
                return "UTF-16BE";
            }
            if (b0 == 0xFF && b1 == 0xFE) {
                return "UTF-16LE";
            }

            return null;

        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Reads up to {@code maxBytes} bytes from the beginning of the file.
     */
    private static byte[] readBytes(File file, int maxBytes) throws IOException {
        if (!file.exists() || !file.isFile()) {
            return null;
        }

        InputStream is = null;
        try {
            is = new BufferedInputStream(new FileInputStream(file));
            byte[] buffer = new byte[maxBytes];
            int totalRead = 0;
            int read;
            while (totalRead < maxBytes &&
                    (read = is.read(buffer, totalRead, maxBytes - totalRead)) != -1) {
                totalRead += read;
            }
            if (totalRead == 0) {
                return null;
            }
            if (totalRead < maxBytes) {
                return Arrays.copyOf(buffer, totalRead);
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
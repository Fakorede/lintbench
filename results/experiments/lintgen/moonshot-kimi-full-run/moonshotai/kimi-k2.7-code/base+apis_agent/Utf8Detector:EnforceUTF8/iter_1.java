package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class Utf8Detector extends Detector implements Detector.XmlScanner {
    private static final Implementation IMPLEMENTATION =
            new Implementation(Utf8Detector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE = Issue.create(
            "EnforceUTF8",
            "Encoding should be UTF-8",
            "XML supports encoding in a wide variety of character sets. However, not all "
                    + "tools handle the XML encoding attribute correctly, and nearly all Android "
                    + "apps use UTF-8, so by using UTF-8 you can protect yourself against subtle "
                    + "bugs when using non-ASCII characters.\n\n"
                    + "In particular, the Android Gradle build system will merge resource XML files "
                    + "assuming the resource files are using UTF-8 encoding.",
            Category.I18N,
            3,
            Severity.WARNING,
            IMPLEMENTATION);

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        byte[] bytes = context.readFileAsBytes();
        if (bytes == null || bytes.length == 0) {
            return;
        }

        String encoding = detectEncoding(bytes);
        if (encoding != null && !encoding.equalsIgnoreCase("UTF-8")) {
            context.report(ISSUE, context.getLocation(document),
                    "Resource file is encoded as " + encoding + " instead of UTF-8");
        }
    }

    @Nullable
    private static String detectEncoding(@NonNull byte[] bytes) {
        String bomEncoding = detectBom(bytes);
        if (bomEncoding != null) {
            return bomEncoding;
        }

        String noBomEncoding = detectEncodingWithoutBom(bytes);
        if (noBomEncoding != null) {
            return noBomEncoding;
        }

        return getDeclaredEncoding(bytes);
    }

    @Nullable
    private static String detectBom(@NonNull byte[] bytes) {
        if (bytes.length >= 4) {
            if (bytes[0] == 0x00 && bytes[1] == 0x00
                    && bytes[2] == (byte) 0xFE && bytes[3] == (byte) 0xFF) {
                return "UTF-32";
            }
            if (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xFE
                    && bytes[2] == 0x00 && bytes[3] == 0x00) {
                return "UTF-32";
            }
        }
        if (bytes.length >= 2) {
            if (bytes[0] == (byte) 0xFE && bytes[1] == (byte) 0xFF) {
                return "UTF-16";
            }
            if (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xFE) {
                return "UTF-16";
            }
            if (bytes.length >= 3 && bytes[0] == (byte) 0xEF
                    && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF) {
                return "UTF-8";
            }
        }
        return null;
    }

    @Nullable
    private static String detectEncodingWithoutBom(@NonNull byte[] bytes) {
        if (bytes.length >= 4) {
            if (bytes[0] == 0x00 && bytes[1] == 0x00
                    && bytes[2] == 0x00 && bytes[3] == '<') {
                return "UTF-32";
            }
            if (bytes[0] == '<' && bytes[1] == 0x00
                    && bytes[2] == 0x00 && bytes[3] == 0x00) {
                return "UTF-32";
            }
        }
        if (bytes.length >= 2) {
            if (bytes[0] == 0x00 && bytes[1] == '<') {
                return "UTF-16";
            }
            if (bytes[0] == '<' && bytes[1] == 0x00) {
                return "UTF-16";
            }
        }
        return null;
    }

    @Nullable
    private static String getDeclaredEncoding(@NonNull byte[] bytes) {
        int start = 0;
        // Skip UTF-8 BOM.
        if (bytes.length >= 3 && bytes[0] == (byte) 0xEF
                && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF) {
            start = 3;
        }

        if (start + 5 > bytes.length) {
            return null;
        }

        if (bytes[start] != '<' || bytes[start + 1] != '?'
                || bytes[start + 2] != 'x' || bytes[start + 3] != 'm'
                || bytes[start + 4] != 'l') {
            return null;
        }

        int end = start + 5;
        while (end + 1 < bytes.length) {
            if (bytes[end] == '?' && bytes[end + 1] == '>') {
                break;
            }
            end++;
        }
        if (end + 1 >= bytes.length) {
            return null;
        }

        for (int i = start; i < end - 7; i++) {
            if (bytes[i] == 'e' && bytes[i + 1] == 'n'
                    && bytes[i + 2] == 'c' && bytes[i + 3] == 'o'
                    && bytes[i + 4] == 'd' && bytes[i + 5] == 'i'
                    && bytes[i + 6] == 'n' && bytes[i + 7] == 'g') {
                int eq = i + 8;
                while (eq < end && isXmlWhitespace(bytes[eq])) {
                    eq++;
                }
                if (eq < end && bytes[eq] == '=') {
                    eq++;
                    while (eq < end && isXmlWhitespace(bytes[eq])) {
                        eq++;
                    }
                    if (eq < end) {
                        byte quote = bytes[eq];
                        if (quote == '"' || quote == '\'') {
                            int valueStart = eq + 1;
                            int valueEnd = valueStart;
                            while (valueEnd < end && bytes[valueEnd] != quote) {
                                valueEnd++;
                            }
                            if (valueEnd < end) {
                                return new String(bytes, valueStart, valueEnd - valueStart,
                                        StandardCharsets.US_ASCII);
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    private static boolean isXmlWhitespace(byte b) {
        return b == ' ' || b == '\t' || b == '\r' || b == '\n';
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
    }
}
package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

public class Utf8Detector extends ResourceXmlDetector {

    private static final String UTF8 = "UTF-8";

    private static final Implementation IMPLEMENTATION =
            new Implementation(Utf8Detector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "EnforceUTF8",
                    "Encoding used in resource files is not UTF-8",
                    "XML supports encoding in a wide variety of character sets. However, not all tools handle the XML encoding attribute correctly, and nearly all Android apps use UTF-8, so by using UTF-8 you can protect yourself against subtle bugs when using non-ASCII characters.\n\n"
                            + "In particular, the Android Gradle build system will merge resource XML files assuming the resource files are using UTF-8 encoding.",
                    Category.I18N,
                    5,
                    Severity.FATAL,
                    IMPLEMENTATION);

    @Override
    public void visitDocument(XmlContext context, org.w3c.dom.Document document) {
        java.io.File file = context.getFile();
        if (file == null || !file.exists()) {
            return;
        }

        String declaredEncoding = document.getXmlEncoding();
        if (declaredEncoding != null && !declaredEncoding.equalsIgnoreCase(UTF8)) {
            report(context, document, declaredEncoding);
            return;
        }

        byte[] bytes = readFileBytes(file);
        if (bytes == null) {
            return;
        }

        String detectedEncoding = detectEncodingFromBom(bytes);
        if (detectedEncoding != null && !detectedEncoding.equalsIgnoreCase(UTF8)) {
            report(context, document, detectedEncoding);
        }
    }

    private static byte[] readFileBytes(java.io.File file) {
        try {
            return java.nio.file.Files.readAllBytes(file.toPath());
        } catch (java.io.IOException e) {
            return null;
        }
    }

    private static String detectEncodingFromBom(byte[] bytes) {
        if (bytes.length >= 4) {
            if (bytes[0] == (byte) 0x00 && bytes[1] == (byte) 0x00
                    && bytes[2] == (byte) 0xFE && bytes[3] == (byte) 0xFF) {
                return "UTF-32BE";
            }
            if (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xFE
                    && bytes[2] == (byte) 0x00 && bytes[3] == (byte) 0x00) {
                return "UTF-32LE";
            }
        }
        if (bytes.length >= 2) {
            if (bytes[0] == (byte) 0xFE && bytes[1] == (byte) 0xFF) {
                return "UTF-16BE";
            }
            if (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xFE) {
                return "UTF-16LE";
            }
            if (bytes.length >= 3 && bytes[0] == (byte) 0xEF
                    && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF) {
                return UTF8;
            }
        }
        return null;
    }

    private static void report(XmlContext context, org.w3c.dom.Document document, String encoding) {
        Location location = context.getLocation(document);
        String message = "The resource file is encoded as " + encoding
                + " (UTF-8 is recommended for Android resource files)";
        context.report(ISSUE, location, message);
    }
}
package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
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
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utf8Detector extends ResourceXmlDetector {

    private static final String UTF_8 = "UTF-8";
    private static final int MAX_HEADER_BYTES = 200;

    private static final byte[] UTF8_BOM = new byte[] {
            (byte) 0xEF, (byte) 0xBB, (byte) 0xBF
    };
    private static final byte[] UTF16BE_BOM = new byte[] {
            (byte) 0xFE, (byte) 0xFF
    };
    private static final byte[] UTF16LE_BOM = new byte[] {
            (byte) 0xFF, (byte) 0xFE
    };
    private static final byte[] UTF32BE_BOM = new byte[] {
            0x00, 0x00, (byte) 0xFE, (byte) 0xFF
    };
    private static final byte[] UTF32LE_BOM = new byte[] {
            (byte) 0xFF, (byte) 0xFE, 0x00, 0x00
    };

    private static final Pattern ENCODING_PATTERN =
            Pattern.compile("encoding\\s*=\\s*([\"'])(.*?)\\1");

    public static final Issue ISSUE = Issue.create(
            "EnforceUTF8",
            "Encoding used in resource files is not UTF-8",
            "XML supports encoding in a wide variety of character sets. However, not all "
                    + "tools handle the XML encoding attribute correctly, and nearly all Android "
                    + "apps use UTF-8, so by using UTF-8 you can protect yourself against subtle "
                    + "bugs when using non-ASCII characters.\n\n"
                    + "In particular, the Android Gradle build system will merge resource XML files "
                    + "assuming the resource files are using UTF-8 encoding.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(Utf8Detector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public void visitDocument(XmlContext context, Document document) {
        File file = context.file;
        byte[] bytes = readFirstBytes(file, MAX_HEADER_BYTES);
        if (bytes == null) {
            return;
        }

        String encoding = getEncoding(bytes);
        if (encoding != null && !encoding.equalsIgnoreCase(UTF_8)) {
            context.report(
                    ISSUE,
                    Location.create(file),
                    "The XML encoding declaration must be UTF-8, was \"" + encoding + "\"");
        }
    }

    private static byte[] readFirstBytes(File file, int max) {
        try (FileInputStream stream = new FileInputStream(file)) {
            byte[] buffer = new byte[max];
            int read = stream.read(buffer);
            if (read <= 0) {
                return null;
            }
            if (read < max) {
                byte[] actual = new byte[read];
                System.arraycopy(buffer, 0, actual, 0, read);
                return actual;
            }
            return buffer;
        } catch (IOException e) {
            return null;
        }
    }

    private static String getEncoding(byte[] bytes) {
        if (startsWith(bytes, UTF32BE_BOM)) {
            return "UTF-32";
        }
        if (startsWith(bytes, UTF32LE_BOM)) {
            return "UTF-32";
        }
        if (startsWith(bytes, UTF16BE_BOM)) {
            return "UTF-16";
        }
        if (startsWith(bytes, UTF16LE_BOM)) {
            return "UTF-16";
        }

        int start = 0;
        if (startsWith(bytes, UTF8_BOM)) {
            start = UTF8_BOM.length;
        }

        for (int i = start; i < bytes.length && i < start + 100; i++) {
            if (bytes[i] == 0) {
                return "UTF-16/UTF-32";
            }
        }

        String header = new String(bytes, start, bytes.length - start,
                StandardCharsets.ISO_8859_1);
        int xmlDeclStart = header.indexOf("<?xml");
        if (xmlDeclStart == -1) {
            return null;
        }
        int xmlDeclEnd = header.indexOf("?>", xmlDeclStart);
        if (xmlDeclEnd == -1) {
            return null;
        }
        String declaration = header.substring(xmlDeclStart, xmlDeclEnd + 2);
        Matcher matcher = ENCODING_PATTERN.matcher(declaration);
        if (matcher.find()) {
            return matcher.group(2);
        }

        return null;
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
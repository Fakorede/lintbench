package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScannerConstants;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class Utf8Detector extends Detector implements Detector.XmlScanner {

    private static final String UTF8 = "UTF-8";

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
            new Implementation(Utf8Detector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return true;
    }

    @Override
    @NonNull
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    @Nullable
    public Collection<String> getApplicableAttributes() {
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Encoding is checked once per document in visitDocument.
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        // Not needed.
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        checkEncoding(context);
    }

    private void checkEncoding(@NonNull XmlContext context) {
        File file = context.getFile();
        if (!file.exists()) {
            return;
        }

        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            return;
        }

        if (bytes.length == 0) {
            return;
        }

        String encoding = null;
        int offset = 0;

        if (bytes.length >= 4
                && bytes[0] == 0x00 && bytes[1] == 0x00
                && bytes[2] == (byte) 0xFE && bytes[3] == (byte) 0xFF) {
            encoding = "UTF-32BE";
        } else if (bytes.length >= 4
                && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xFE
                && bytes[2] == 0x00 && bytes[3] == 0x00) {
            encoding = "UTF-32LE";
        } else if (bytes.length >= 2
                && bytes[0] == (byte) 0xFE && bytes[1] == (byte) 0xFF) {
            encoding = "UTF-16BE";
        } else if (bytes.length >= 2
                && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xFE) {
            encoding = "UTF-16LE";
        } else if (bytes.length >= 3
                && bytes[0] == (byte) 0xEF
                && bytes[1] == (byte) 0xBB
                && bytes[2] == (byte) 0xBF) {
            offset = 3;
        }

        if (encoding != null) {
            report(context, encoding);
            return;
        }

        if (offset + 3 < bytes.length) {
            if (bytes[offset] == 0x00 && bytes[offset + 1] == 0x00
                    && bytes[offset + 2] == 0x00 && bytes[offset + 3] == '<') {
                encoding = "UTF-32BE";
            } else if (bytes[offset] == '<' && bytes[offset + 1] == 0x00
                    && bytes[offset + 2] == 0x00 && bytes[offset + 3] == 0x00) {
                encoding = "UTF-32LE";
            } else if (bytes[offset] == 0x00 && bytes[offset + 1] == '<'
                    && bytes[offset + 2] == 0x00 && bytes[offset + 3] == '?') {
                encoding = "UTF-16BE";
            } else if (bytes[offset] == '<' && bytes[offset + 1] == 0x00
                    && bytes[offset + 2] == '?' && bytes[offset + 3] == 0x00) {
                encoding = "UTF-16LE";
            }
        }

        if (encoding != null) {
            report(context, encoding);
            return;
        }

        String text = new String(bytes, offset, bytes.length - offset, StandardCharsets.ISO_8859_1);
        if (text.startsWith("<?xml")) {
            int end = text.indexOf("?>");
            if (end != -1) {
                String declared = extractEncoding(text.substring(0, end + 2));
                if (declared != null && !declared.equalsIgnoreCase(UTF8)) {
                    report(context, declared);
                }
            }
        }
    }

    @Nullable
    private static String extractEncoding(@NonNull String declaration) {
        int index = declaration.indexOf("encoding");
        if (index == -1) {
            return null;
        }
        index += "encoding".length();

        int length = declaration.length();
        while (index < length && Character.isWhitespace(declaration.charAt(index))) {
            index++;
        }
        if (index >= length || declaration.charAt(index) != '=') {
            return null;
        }
        index++;

        while (index < length && Character.isWhitespace(declaration.charAt(index))) {
            index++;
        }
        if (index >= length) {
            return null;
        }

        char quote = declaration.charAt(index);
        if (quote != '"' && quote != '\'') {
            return null;
        }
        int start = ++index;
        int end = declaration.indexOf(quote, start);
        if (end == -1) {
            return null;
        }
        return declaration.substring(start, end);
    }

    private static void report(@NonNull XmlContext context, @NonNull String encoding) {
        context.report(
                ISSUE,
                Location.create(context.getFile()),
                "The resource file is using encoding \"" + encoding + "\" instead of UTF-8.");
    }
}
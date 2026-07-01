package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

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
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(Utf8Detector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return true;
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        File file = context.file;
        if (file == null || !file.exists()) {
            return;
        }

        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file.toPath());
        } catch (Exception e) {
            return;
        }

        if (bytes.length == 0) {
            return;
        }

        // Check for non-UTF-8 BOMs (check 4-byte BOMs before 2-byte to avoid false positives)
        if (bytes.length >= 4) {
            if (bytes[0] == 0x00 && bytes[1] == 0x00 && bytes[2] == (byte) 0xFE && bytes[3] == (byte) 0xFF) {
                reportEncodingIssue(context, document);
                return;
            }
            if (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xFE && bytes[2] == 0x00 && bytes[3] == 0x00) {
                reportEncodingIssue(context, document);
                return;
            }
        }
        if (bytes.length >= 2) {
            if (bytes[0] == (byte) 0xFE && bytes[1] == (byte) 0xFF) {
                reportEncodingIssue(context, document);
                return;
            }
            if (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xFE) {
                reportEncodingIssue(context, document);
                return;
            }
        }

        // Check XML declaration for encoding attribute
        int xmlStart = -1;
        for (int i = 0; i < bytes.length - 4; i++) {
            if (bytes[i] == '<' && bytes[i + 1] == '?' && bytes[i + 2] == 'x' && bytes[i + 3] == 'm' && bytes[i + 4] == 'l') {
                xmlStart = i;
                break;
            }
        }

        if (xmlStart != -1) {
            int searchLimit = Math.min(xmlStart + 200, bytes.length);
            String header = new String(bytes, xmlStart, searchLimit - xmlStart, StandardCharsets.US_ASCII);
            String headerLower = header.toLowerCase();
            int encIdx = headerLower.indexOf("encoding");
            if (encIdx != -1) {
                int eqIdx = headerLower.indexOf('=', encIdx);
                if (eqIdx != -1) {
                    int quoteIdx = eqIdx + 1;
                    while (quoteIdx < header.length() && Character.isWhitespace(header.charAt(quoteIdx))) {
                        quoteIdx++;
                    }
                    if (quoteIdx < header.length()) {
                        char quote = header.charAt(quoteIdx);
                        if (quote == '"' || quote == '\'') {
                            int endQuote = header.indexOf(quote, quoteIdx + 1);
                            if (endQuote != -1) {
                                String enc = header.substring(quoteIdx + 1, endQuote);
                                if (!enc.equalsIgnoreCase("UTF-8")) {
                                    reportEncodingIssue(context, document);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void reportEncodingIssue(XmlContext context, Document document) {
        context.report(ISSUE, context.getLocation(document),
                "The file is not encoded as UTF-8; this can lead to subtle bugs when the file is merged with other resources. Consider converting to UTF-8.");
    }
}
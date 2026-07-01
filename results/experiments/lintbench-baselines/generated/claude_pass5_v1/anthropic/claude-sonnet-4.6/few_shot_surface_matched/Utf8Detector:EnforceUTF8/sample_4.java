package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

public class Utf8Detector extends ResourceXmlDetector {

    public static final Issue ISSUE =
            Issue.create(
                    "EnforceUTF8",
                    "Resource file is not UTF-8",
                    "XML supports encoding in a wide variety of character sets. However, not all "
                            + "tools handle the XML encoding attribute correctly, and nearly all Android "
                            + "apps use UTF-8, so by using UTF-8 you can protect yourself against subtle "
                            + "bugs when using non-ASCII characters.\n"
                            + "\n"
                            + "In particular, the Android Gradle build system will merge resource XML files "
                            + "assuming the resource files are using UTF-8 encoding.",
                    Category.I18N,
                    8,
                    Severity.FATAL,
                    new Implementation(Utf8Detector.class, Scope.RESOURCE_FILE_SCOPE));

    public Utf8Detector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return true;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        String encoding = null;

        // Check the XML declaration for the encoding attribute
        // The XML declaration is represented as a processing instruction or
        // can be found via the document's input encoding
        String inputEncoding = document.getInputEncoding();
        if (inputEncoding != null) {
            encoding = inputEncoding;
        }

        // Also check via the XML prologue/processing instruction
        Node firstChild = document.getFirstChild();
        if (firstChild != null && firstChild.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE) {
            String data = firstChild.getNodeValue();
            if (data != null && data.contains("encoding")) {
                int index = data.indexOf("encoding");
                if (index != -1) {
                    int start = data.indexOf('"', index);
                    int singleStart = data.indexOf('\'', index);
                    char quote;
                    if (start == -1 && singleStart == -1) {
                        // No encoding value found
                        return;
                    } else if (start == -1) {
                        start = singleStart;
                        quote = '\'';
                    } else if (singleStart == -1) {
                        quote = '"';
                    } else {
                        if (singleStart < start) {
                            start = singleStart;
                            quote = '\'';
                        } else {
                            quote = '"';
                        }
                    }
                    int end = data.indexOf(quote, start + 1);
                    if (end != -1) {
                        encoding = data.substring(start + 1, end);
                    }
                }
            }
        }

        if (encoding != null && !encoding.equalsIgnoreCase("utf-8")
                && !encoding.equalsIgnoreCase("utf8")) {
            String message =
                    String.format(
                            "Resource files should be encoded in UTF-8 to avoid subtle "
                                    + "compatibility issues with the build system and various "
                                    + "tools; current encoding is `%1$s`",
                            encoding);
            context.report(
                    ISSUE,
                    document.getDocumentElement() != null
                            ? context.getLocation(document.getDocumentElement())
                            : context.getLocation(firstChild),
                    message);
        }
    }
}
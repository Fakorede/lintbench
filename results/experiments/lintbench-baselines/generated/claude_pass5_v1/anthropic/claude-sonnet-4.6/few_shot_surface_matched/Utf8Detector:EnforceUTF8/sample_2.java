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
import org.w3c.dom.DocumentType;

public class Utf8Detector extends ResourceXmlDetector {

    public static final Issue ISSUE =
            Issue.create(
                    "EnforceUTF8",
                    "Wrong file encoding",
                    "Encoding used in resource files is not UTF-8\n"
                            + "\n"
                            + "XML supports encoding in a wide variety of character sets. However, "
                            + "not all tools handle the XML encoding attribute correctly, and nearly "
                            + "all Android apps use UTF-8, so by using UTF-8 you can protect yourself "
                            + "against subtle bugs when using non-ASCII characters.\n"
                            + "\n"
                            + "In particular, the Android Gradle build system will merge resource XML "
                            + "files assuming the resource files are using UTF-8 encoding.",
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
        String contents = context.getContents();
        if (contents == null) {
            return;
        }

        // Check for an XML declaration with an encoding attribute
        // The XML declaration looks like: <?xml version="1.0" encoding="..."?>
        if (contents.startsWith("<?xml")) {
            int endOfDeclaration = contents.indexOf("?>");
            if (endOfDeclaration != -1) {
                String declaration = contents.substring(0, endOfDeclaration + 2);
                int encodingIndex = declaration.indexOf("encoding");
                if (encodingIndex != -1) {
                    // Find the encoding value
                    int equalsIndex = declaration.indexOf('=', encodingIndex);
                    if (equalsIndex != -1) {
                        int start = equalsIndex + 1;
                        // Skip whitespace
                        while (start < declaration.length()
                                && Character.isWhitespace(declaration.charAt(start))) {
                            start++;
                        }
                        if (start < declaration.length()) {
                            char quote = declaration.charAt(start);
                            if (quote == '"' || quote == '\'') {
                                int end = declaration.indexOf(quote, start + 1);
                                if (end != -1) {
                                    String encoding =
                                            declaration.substring(start + 1, end);
                                    if (!encoding.equalsIgnoreCase("utf-8")
                                            && !encoding.equalsIgnoreCase("utf8")) {
                                        // Report the issue
                                        context.report(
                                                ISSUE,
                                                document,
                                                context.getLocation(document),
                                                "Resource files should be saved with UTF-8 encoding; "
                                                        + "found `"
                                                        + encoding
                                                        + "`");
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
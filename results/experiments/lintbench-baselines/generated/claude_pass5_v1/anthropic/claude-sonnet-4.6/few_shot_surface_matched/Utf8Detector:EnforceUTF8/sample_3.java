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
        String contents = context.getContents();
        if (contents == null) {
            return;
        }

        // Check if the document has an XML declaration with an encoding attribute
        // that is not UTF-8
        if (contents.startsWith("<?xml")) {
            int endIndex = contents.indexOf("?>");
            if (endIndex == -1) {
                return;
            }
            String declaration = contents.substring(0, endIndex + 2);
            int encodingIndex = declaration.indexOf("encoding");
            if (encodingIndex == -1) {
                // No encoding declaration; default is UTF-8 for XML, so this is fine
                return;
            }

            // Extract the encoding value
            int equalsIndex = declaration.indexOf('=', encodingIndex);
            if (equalsIndex == -1) {
                return;
            }
            int start = equalsIndex + 1;
            // Skip whitespace
            while (start < declaration.length() && Character.isWhitespace(declaration.charAt(start))) {
                start++;
            }
            if (start >= declaration.length()) {
                return;
            }
            char quote = declaration.charAt(start);
            if (quote != '"' && quote != '\'') {
                return;
            }
            int valueStart = start + 1;
            int valueEnd = declaration.indexOf(quote, valueStart);
            if (valueEnd == -1) {
                return;
            }
            String encoding = declaration.substring(valueStart, valueEnd);
            if (!encoding.equalsIgnoreCase("utf-8") && !encoding.equalsIgnoreCase("utf8")) {
                context.report(
                        ISSUE,
                        context.getLocation(document),
                        "Resource files should be encoded in UTF-8 (found `" + encoding + "`)");
            }
        }
    }
}
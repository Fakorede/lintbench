package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Document;

public class Utf8Detector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(Utf8Detector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "EnforceUTF8",
                    "Encoding used in resource files is not UTF-8",
                    "XML supports encoding in a wide variety of character sets. However, not all "
                            + "tools handle the XML encoding attribute correctly, and nearly all Android "
                            + "apps use UTF-8, so by using UTF-8 you can protect yourself against subtle "
                            + "bugs when using non-ASCII characters.\n"
                            + "\n"
                            + "In particular, the Android Gradle build system will merge resource XML files "
                            + "assuming the resource files are using UTF-8 encoding.",
                    Category.I18N,
                    5,
                    Severity.FATAL,
                    IMPLEMENTATION);

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        String xmlDeclaration = context.getContents();
        if (xmlDeclaration == null) {
            return;
        }

        // Check if there's an XML declaration with an encoding attribute
        if (!xmlDeclaration.startsWith("<?xml")) {
            // No XML declaration at all; defaults to UTF-8, which is fine
            return;
        }

        // Find the end of the XML declaration
        int endIndex = xmlDeclaration.indexOf("?>");
        if (endIndex == -1) {
            return;
        }

        String declaration = xmlDeclaration.substring(0, endIndex + 2);

        // Look for encoding attribute in the declaration
        int encodingIndex = declaration.indexOf("encoding");
        if (encodingIndex == -1) {
            // No encoding specified; defaults to UTF-8, which is fine
            return;
        }

        // Extract the encoding value
        int equalsIndex = declaration.indexOf('=', encodingIndex);
        if (equalsIndex == -1) {
            return;
        }

        // Skip whitespace after '='
        int valueStart = equalsIndex + 1;
        while (valueStart < declaration.length() && Character.isWhitespace(declaration.charAt(valueStart))) {
            valueStart++;
        }

        if (valueStart >= declaration.length()) {
            return;
        }

        char quote = declaration.charAt(valueStart);
        if (quote != '\'' && quote != '"') {
            return;
        }

        int valueEnd = declaration.indexOf(quote, valueStart + 1);
        if (valueEnd == -1) {
            return;
        }

        String encoding = declaration.substring(valueStart + 1, valueEnd);

        if (!encoding.equalsIgnoreCase("utf-8") && !encoding.equalsIgnoreCase("utf8")) {
            // Calculate the location of the encoding value in the document
            int locationStart = valueStart + 1; // skip the opening quote
            context.report(
                    ISSUE,
                    context.getLocation(context.document),
                    "Resource file is not encoded in UTF-8: encoding is `" + encoding + "`");
        }
    }
}
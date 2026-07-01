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
                            + "bugs when using non-ASCII characters.\n\n"
                            + "In particular, the Android Gradle build system will merge resource XML files "
                            + "assuming the resource files are using UTF-8 encoding.",
                    Category.I18N,
                    5,
                    Severity.FATAL,
                    IMPLEMENTATION);

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        String xmlDeclaration = getXmlDeclaration(context);
        if (xmlDeclaration != null) {
            // Check if encoding is explicitly specified
            int encodingIndex = xmlDeclaration.toLowerCase().indexOf("encoding");
            if (encodingIndex != -1) {
                // Extract the encoding value
                int equalsIndex = xmlDeclaration.indexOf('=', encodingIndex);
                if (equalsIndex != -1) {
                    int start = equalsIndex + 1;
                    // Skip whitespace
                    while (start < xmlDeclaration.length()
                            && Character.isWhitespace(xmlDeclaration.charAt(start))) {
                        start++;
                    }
                    if (start < xmlDeclaration.length()) {
                        char quote = xmlDeclaration.charAt(start);
                        if (quote == '"' || quote == '\'') {
                            int end = xmlDeclaration.indexOf(quote, start + 1);
                            if (end != -1) {
                                String encoding = xmlDeclaration.substring(start + 1, end);
                                if (!encoding.equalsIgnoreCase("utf-8")
                                        && !encoding.equalsIgnoreCase("utf8")) {
                                    context.report(
                                            ISSUE,
                                            context.getLocation(document),
                                            "Resource files should be encoded in UTF-8 (current encoding is `"
                                                    + encoding
                                                    + "`)");
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private String getXmlDeclaration(@NonNull XmlContext context) {
        CharSequence contents = context.getContents();
        if (contents == null) {
            return null;
        }
        String text = contents.toString();
        if (text.startsWith("<?xml")) {
            int end = text.indexOf("?>");
            if (end != -1) {
                return text.substring(0, end + 2);
            }
        }
        return null;
    }
}
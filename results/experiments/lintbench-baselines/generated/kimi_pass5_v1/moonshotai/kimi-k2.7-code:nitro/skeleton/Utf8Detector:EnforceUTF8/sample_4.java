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

    private static final String EXPECTED_ENCODING = "UTF-8";

    private static final Implementation IMPLEMENTATION =
            new Implementation(Utf8Detector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "EnforceUTF8",
                    "Encoding used in resource files is not UTF-8",
                    "XML supports encoding in a wide variety of character sets. However, not all "
                            + "tools handle the XML encoding attribute correctly, and nearly all "
                            + "Android apps use UTF-8, so by using UTF-8 you can protect yourself "
                            + "against subtle bugs when using non-ASCII characters.\n\n"
                            + "In particular, the Android Gradle build system will merge resource "
                            + "XML files assuming the resource files are using UTF-8 encoding.",
                    Category.I18N,
                    5,
                    Severity.FATAL,
                    IMPLEMENTATION);

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        CharSequence content = context.getContents();
        if (content == null || content.length() == 0) {
            return;
        }

        if (content.charAt(0) == '\uFEFF') {
            context.report(
                    ISSUE,
                    document,
                    context.getLocation(document),
                    "Resource files should be encoded as UTF-8 without a byte-order mark");
            return;
        }

        int i = 0;
        int length = content.length();
        while (i < length && Character.isWhitespace(content.charAt(i))) {
            i++;
        }

        String text = content.toString();
        if (i >= length || !text.startsWith("<?xml", i)) {
            return;
        }

        int end = text.indexOf("?>", i);
        if (end == -1) {
            return;
        }

        String declaration = text.substring(i, end + 2);
        String encoding = extractEncoding(declaration);
        if (encoding == null) {
            return;
        }

        if (!EXPECTED_ENCODING.equalsIgnoreCase(encoding)) {
            String message = String.format(
                    "The XML encoding declaration is \"%1$s\"; resource files must use UTF-8",
                    encoding);
            context.report(ISSUE, document, context.getLocation(document), message);
        }
    }

    private static String extractEncoding(String declaration) {
        int index = declaration.indexOf("encoding");
        if (index == -1) {
            return null;
        }

        int length = declaration.length();
        int i = index + "encoding".length();
        while (i < length && Character.isWhitespace(declaration.charAt(i))) {
            i++;
        }
        if (i >= length || declaration.charAt(i) != '=') {
            return null;
        }
        i++;

        while (i < length && Character.isWhitespace(declaration.charAt(i))) {
            i++;
        }
        if (i >= length) {
            return null;
        }

        char quote = declaration.charAt(i);
        if (quote != '\"' && quote != '\'') {
            return null;
        }

        int start = i + 1;
        int end = declaration.indexOf(quote, start);
        if (end == -1) {
            return null;
        }

        return declaration.substring(start, end);
    }
}
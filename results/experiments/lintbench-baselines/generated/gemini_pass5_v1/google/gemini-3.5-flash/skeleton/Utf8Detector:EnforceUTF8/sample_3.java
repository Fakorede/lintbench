package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
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
        CharSequence contents = context.getContents();
        if (contents == null) {
            return;
        }

        boolean reported = false;
        String contentStr = contents.toString();
        int xmlIndex = contentStr.indexOf("<?xml");
        if (xmlIndex != -1) {
            int closeIndex = contentStr.indexOf("?>", xmlIndex);
            if (closeIndex != -1) {
                String prologue = contentStr.substring(xmlIndex, closeIndex);
                int encodingKeyIndex = prologue.indexOf("encoding");
                if (encodingKeyIndex != -1) {
                    int eqIndex = prologue.indexOf('=', encodingKeyIndex);
                    if (eqIndex != -1) {
                        int firstQuote = -1;
                        char quoteChar = 0;
                        for (int i = eqIndex + 1; i < prologue.length(); i++) {
                            char c = prologue.charAt(i);
                            if (c == '"' || c == '\'') {
                                firstQuote = i;
                                quoteChar = c;
                                break;
                            } else if (!Character.isWhitespace(c)) {
                                break;
                            }
                        }
                        if (firstQuote != -1) {
                            int secondQuote = prologue.indexOf(quoteChar, firstQuote + 1);
                            if (secondQuote != -1) {
                                String encoding = prologue.substring(firstQuote + 1, secondQuote);
                                if (!encoding.equalsIgnoreCase("utf-8") && !encoding.equalsIgnoreCase("utf8")) {
                                    int start = xmlIndex + firstQuote + 1;
                                    int end = xmlIndex + secondQuote;
                                    Location location = Location.create(context.file, contents, start, end);
                                    context.report(ISSUE, location, "Encoding should be UTF-8 (found " + encoding + ")");
                                    reported = true;
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!reported) {
            String docEncoding = null;
            try {
                docEncoding = document.getXmlEncoding();
            } catch (Throwable t) {
                // ignore
            }
            if (docEncoding != null && !docEncoding.equalsIgnoreCase("utf-8") && !docEncoding.equalsIgnoreCase("utf8")) {
                context.report(ISSUE, context.getLocation(document), "Encoding should be UTF-8 (found " + docEncoding + ")");
            }
        }
    }
}
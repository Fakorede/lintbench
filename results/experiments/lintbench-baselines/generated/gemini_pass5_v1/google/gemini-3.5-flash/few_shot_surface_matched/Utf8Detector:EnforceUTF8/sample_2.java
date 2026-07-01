package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

public class Utf8Detector extends ResourceXmlDetector implements XmlScanner {

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
                    new Implementation(
                            Utf8Detector.class,
                            Scope.RESOURCE_FILE_SCOPE));

    @Override
    public void visitDocument(XmlContext context, org.w3c.dom.Document document) {
        String encoding = document.getXmlEncoding();
        if (encoding == null) {
            CharSequence contents = context.getContents();
            if (contents != null) {
                String text = contents.toString();
                if (text.startsWith("<?xml")) {
                    int prologueEnd = text.indexOf("?>");
                    if (prologueEnd != -1) {
                        String prologue = text.substring(0, prologueEnd);
                        int encodingIndex = prologue.indexOf("encoding");
                        if (encodingIndex != -1) {
                            int firstQuote = -1;
                            char quote = 0;
                            for (int i = encodingIndex + 8; i < prologue.length(); i++) {
                                char c = prologue.charAt(i);
                                if (c == '"' || c == '\'') {
                                    if (firstQuote == -1) {
                                        firstQuote = i + 1;
                                        quote = c;
                                    } else if (c == quote) {
                                        encoding = prologue.substring(firstQuote, i).trim();
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (encoding != null && !encoding.equalsIgnoreCase("UTF-8") && !encoding.equalsIgnoreCase("UTF8")) {
            context.report(
                    ISSUE,
                    document,
                    context.getLocation(document),
                    "Encoding used in resource files is not UTF-8");
        }
    }
}
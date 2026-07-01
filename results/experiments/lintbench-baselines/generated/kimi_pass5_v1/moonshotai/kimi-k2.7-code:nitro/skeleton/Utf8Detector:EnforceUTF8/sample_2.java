package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class Utf8Detector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(Utf8Detector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "EnforceUTF8",
                    "Encoding used in resource files is not UTF-8",
                    "XML supports encoding in a wide variety of character sets. However, not all "
                            + "tools handle the XML encoding attribute correctly, and nearly all "
                            + "Android apps use UTF-8, so by using UTF-8 you can protect yourself "
                            + "against subtle bugs when using non-ASCII characters. In particular, "
                            + "the Android Gradle build system will merge resource XML files "
                            + "assuming the resource files are using UTF-8 encoding.",
                    Category.I18N,
                    5,
                    Severity.FATAL,
                    IMPLEMENTATION);

    @Override
    public void visitDocument(XmlContext context, Document document) {
        String encoding = document.getXmlEncoding();
        if (encoding != null && !"UTF-8".equalsIgnoreCase(encoding)) {
            Element root = document.getDocumentElement();
            if (root != null) {
                String message = "The encoding in the XML prolog is \""
                        + encoding
                        + "\", not UTF-8. The Android Gradle build system merges resource XML "
                        + "files assuming UTF-8 encoding, which can lead to subtle bugs with "
                        + "non-ASCII characters.";
                context.report(ISSUE, context.getElementLocation(root), message);
            }
        }
    }
}
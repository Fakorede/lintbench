package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class Utf8Detector extends ResourceXmlDetector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "EnforceUTF8",
                    "Encoding should be UTF-8",
                    "XML supports encoding in a wide variety of character sets. However, not all "
                            + "tools handle the XML encoding attribute correctly, and nearly all "
                            + "Android apps use UTF-8, so by using UTF-8 you can protect yourself "
                            + "against subtle bugs when using non-ASCII characters.\n\n"
                            + "In particular, the Android Gradle build system will merge resource "
                            + "XML files assuming the resource files are using UTF-8 encoding.",
                    Category.I18N,
                    8,
                    Severity.FATAL,
                    new Implementation(Utf8Detector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public void visitDocument(XmlContext context, Document document) {
        String encoding = document.getXmlEncoding();
        if (encoding != null && !"UTF-8".equalsIgnoreCase(encoding)) {
            Element root = document.getDocumentElement();
            if (root != null) {
                context.report(
                        ISSUE,
                        root,
                        context.getLocation(root),
                        "Resource files should use UTF-8 encoding, not " + encoding);
            }
        }
    }
}
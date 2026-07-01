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

    public static final Issue ISSUE =
            Issue.create(
                    "EnforceUTF8",
                    "Resource File Encoding Should Be UTF-8",
                    "XML supports encoding in a wide variety of character sets. However, not all"
                            + " tools handle the XML encoding attribute correctly, and nearly all"
                            + " Android apps use UTF-8, so by using UTF-8 you can protect yourself"
                            + " against subtle bugs when using non-ASCII characters.\n\n"
                            + "In particular, the Android Gradle build system will merge resource"
                            + " XML files assuming the resource files are using UTF-8 encoding.",
                    Category.I18N,
                    8,
                    Severity.FATAL,
                    new Implementation(Utf8Detector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        String encoding = document.getXmlEncoding();
        if (encoding != null
                && !encoding.equalsIgnoreCase("UTF-8")
                && document.getDocumentElement() != null) {
            context.report(
                    ISSUE,
                    document.getDocumentElement(),
                    context.getLocation(document.getDocumentElement()),
                    "Resource files should be encoded as UTF-8 (found: " + encoding + ")");
        }
    }
}
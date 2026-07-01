package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class Utf8Detector extends ResourceXmlDetector {
    public static final Issue ISSUE = Issue.create(
            "EnforceUTF8",
            "Encoding used in resource files is not UTF-8",
            "XML supports encoding in a wide variety of character sets. However, not all " +
            "tools handle the XML encoding attribute correctly, and nearly all Android " +
            "apps use UTF-8, so by using UTF-8 you can protect yourself against subtle " +
            "bugs when using non-ASCII characters.\n\n" +
            "In particular, the Android Gradle build system will merge resource XML files " +
            "assuming the resource files are using UTF-8 encoding.\n",
            Category.CORRECTNESS, 6, Severity.WARNING,
            new Implementation(Utf8Detector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public void visitDocument(@NotNull XmlContext context, @NotNull Document document) {
        String encoding = context.getEncoding();
        if (encoding != null && !encoding.equalsIgnoreCase("UTF-8")) {
            Element root = document.getDocumentElement();
            if (root != null) {
                context.report(ISSUE, root, context.getLocation(root),
                        "The file should be encoded in UTF-8");
            }
        }
    }
}
package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Document;

public class Utf8Detector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "EnforceUTF8",
            "Encoding should be UTF-8",
            "XML supports encoding in a wide variety of character sets. However, not all "
                    + "tools handle the XML encoding attribute correctly, and nearly all Android "
                    + "apps use UTF-8, so by using UTF-8 you can protect yourself against subtle "
                    + "bugs when using non-ASCII characters.\n\n"
                    + "In particular, the Android Gradle build system will merge resource XML files "
                    + "assuming the resource files are using UTF-8 encoding.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(Utf8Detector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public boolean appliesTo(ResourceContext context) {
        return true;
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        String encoding = document.getXmlEncoding();
        if (encoding != null && !encoding.equalsIgnoreCase("UTF-8")) {
            Location location = context.getLocation(document);
            context.report(
                    ISSUE,
                    location,
                    "The XML encoding declaration must be UTF-8, was \"" + encoding + "\"");
        }
    }
}
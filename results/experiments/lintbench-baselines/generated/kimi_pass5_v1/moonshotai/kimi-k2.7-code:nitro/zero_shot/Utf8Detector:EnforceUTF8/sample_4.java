package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import java.util.Collection;
import java.util.Collections;

public class Utf8Detector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
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
            Severity.WARNING,
            new Implementation(Utf8Detector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    @NonNull
    public Collection<Class<? extends Node>> getApplicableNodeTypes() {
        return Collections.singletonList(Document.class);
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        String encoding = document.getXmlEncoding();
        if (encoding != null && !encoding.equalsIgnoreCase("UTF-8")) {
            String message = "Resource file is encoded as " + encoding
                    + " instead of UTF-8. The Android Gradle build system merges "
                    + "resource XML files assuming UTF-8 encoding.";
            context.report(ISSUE, Location.create(context.file), message);
        }
    }
}
package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;
import java.util.Collection;
import java.util.Collections;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingLeanbackSupport",
            "Missing Leanback Support",
            "The manifest should declare the use of the Leanback user interface required by Android TV.\n\n"
                    + "To fix this, add `<uses-feature android:name=\"android.software.leanback\" "
                    + "android:required=\"false\" />` to your manifest.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private boolean hasLeanbackFeature;

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("uses-feature");
    }

    @Override
    public void beforeCheckFile(@NonNull XmlContext context) {
        hasLeanbackFeature = false;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
        if ("android.software.leanback".equals(name)) {
            hasLeanbackFeature = true;
        }
    }

    @Override
    public void afterCheckFile(@NonNull XmlContext context) {
        if (!hasLeanbackFeature) {
            Element root = context.document.getDocumentElement();
            if (root != null) {
                context.report(ISSUE, context.getLocation(root),
                        "The manifest should declare the use of the Leanback user interface required by Android TV.");
            }
        }
    }
}
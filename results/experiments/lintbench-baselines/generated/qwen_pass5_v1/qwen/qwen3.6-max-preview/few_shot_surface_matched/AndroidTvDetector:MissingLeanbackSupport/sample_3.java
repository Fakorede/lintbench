package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
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
            "The manifest should declare the use of the Leanback user interface required by Android TV. " +
            "To fix this, add `<uses-feature android:name=\"android.software.leanback\" android:required=\"false\" />` to your manifest.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private boolean hasLeanbackFeature = false;

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("uses-feature");
    }

    @Override
    public void beforeCheckFile(Context context) {
        hasLeanbackFeature = false;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttribute("android:name");
        if ("android.software.leanback".equals(name)) {
            hasLeanbackFeature = true;
        }
    }

    @Override
    public void afterCheckFile(Context context) {
        if (!hasLeanbackFeature && context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            Element root = xmlContext.document.getDocumentElement();
            if (root != null) {
                xmlContext.report(ISSUE, root, xmlContext.getLocation(root),
                        "The manifest should declare the use of the Leanback user interface required by Android TV. " +
                        "Add `<uses-feature android:name=\"android.software.leanback\" android:required=\"false\" />` to your manifest.");
            }
        }
    }
}
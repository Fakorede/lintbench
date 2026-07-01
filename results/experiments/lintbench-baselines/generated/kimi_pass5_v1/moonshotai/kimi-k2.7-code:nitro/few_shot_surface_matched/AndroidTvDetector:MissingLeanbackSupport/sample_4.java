package com.android.tools.lint.checks;

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

    private static final String USES_FEATURE = "uses-feature";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String LEANBACK_FEATURE = "android.software.leanback";

    public static final Issue ISSUE =
            Issue.create(
                    "MissingLeanbackSupport",
                    "Missing Leanback Support",
                    "The manifest should declare the use of the Leanback user interface required by Android TV. "
                            + "To fix this, add "
                            + "`<uses-feature android:name=\"android.software.leanback\" android:required=\"false\" />` "
                            + "to your manifest.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private boolean mHasLeanbackFeature;

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(XmlContext context) {
        mHasLeanbackFeature = false;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!USES_FEATURE.equals(element.getTagName())) {
            return;
        }

        String name = element.getAttributeNS(ANDROID_URI, "name");
        if (LEANBACK_FEATURE.equals(name)) {
            mHasLeanbackFeature = true;
        }
    }

    @Override
    public void afterCheckFile(XmlContext context) {
        if (!mHasLeanbackFeature) {
            Element root = context.document.getDocumentElement();
            context.report(
                    ISSUE,
                    root,
                    context.getLocation(root),
                    "Missing `<uses-feature android:name=\"android.software.leanback\" android:required=\"false\" />` element required by Android TV");
        }
    }
}
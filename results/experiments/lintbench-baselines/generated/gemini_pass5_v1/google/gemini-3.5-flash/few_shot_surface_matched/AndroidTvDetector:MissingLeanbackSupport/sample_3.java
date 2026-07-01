package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "MissingLeanbackSupport",
                    "Missing Leanback Support",
                    "The manifest should declare the use of the Leanback user interface required "
                            + "by Android TV. To fix this, add `<uses-feature android:name=\"android.software.leanback\" "
                            + "android:required=\"false\" />` to your manifest.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private boolean mHasLeanbackLauncher = false;
    private boolean mHasLeanbackFeature = false;
    private org.w3c.dom.Element mManifestElement = null;

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Arrays.asList("manifest", "uses-feature", "category");
    }

    @Override
    public void beforeCheckFile(XmlContext context) {
        mHasLeanbackLauncher = false;
        mHasLeanbackFeature = false;
        mManifestElement = null;
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        String tagName = element.getTagName();
        if ("manifest".equals(tagName)) {
            mManifestElement = element;
        } else if ("uses-feature".equals(tagName)) {
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if ("android.software.leanback".equals(name)) {
                mHasLeanbackFeature = true;
            }
        } else if ("category".equals(tagName)) {
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                mHasLeanbackLauncher = true;
            }
        }
    }

    @Override
    public void afterCheckFile(XmlContext context) {
        if (mHasLeanbackLauncher && !mHasLeanbackFeature && mManifestElement != null) {
            context.report(
                    ISSUE,
                    mManifestElement,
                    context.getLocation(mManifestElement),
                    "The manifest should declare the use of the Leanback user interface required by Android TV. "
                            + "To fix this, add `<uses-feature android:name=\"android.software.leanback\" "
                            + "android:required=\"false\" />` to your manifest.");
        }
    }
}
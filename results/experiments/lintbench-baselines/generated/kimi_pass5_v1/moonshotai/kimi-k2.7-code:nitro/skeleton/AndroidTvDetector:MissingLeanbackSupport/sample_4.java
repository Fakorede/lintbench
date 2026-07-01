package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "MissingLeanbackSupport",
                    "Missing Leanback Support",
                    "Android TV apps must declare that they use the Leanback user interface by adding a `<uses-feature android:name=\"android.software.leanback\" android:required=\"false\" />` element to the manifest.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean mHasLeanbackFeature;

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Collections.singletonList("uses-feature");
    }

    @Override
    public void beforeCheckFile(Context context) {
        mHasLeanbackFeature = false;
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
        if (name.isEmpty()) {
            name = element.getAttribute("android:name");
        }
        if (name.isEmpty()) {
            name = element.getAttribute("name");
        }
        if ("android.software.leanback".equals(name)) {
            mHasLeanbackFeature = true;
        }
    }

    @Override
    public void afterCheckFile(Context context) {
        if (!mHasLeanbackFeature) {
            org.w3c.dom.Element root = context.document.getDocumentElement();
            context.report(
                    ISSUE,
                    root,
                    context.getElementLocation(root),
                    "Missing Leanback support: add `<uses-feature android:name=\"android.software.leanback\" android:required=\"false\" />` to the manifest.");
        }
    }
}
package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import java.util.Arrays;
import java.util.Collection;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "MissingLeanbackSupport",
                    "Missing Leanback Support",
                    "The manifest should declare the use of the Leanback user interface required"
                            + " by Android TV. To fix this, add `<uses-feature"
                            + " android:name=\"android.software.leanback\""
                            + " android:required=\"false\" />` to your manifest.",
                    Category.CORRECTNESS,
                    8,
                    Severity.ERROR,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String LEANBACK_FEATURE = "android.software.leanback";
    private static final String LEANBACK_LAUNCHER = "android.intent.category.LEANBACK_LAUNCHER";

    private boolean mHasLeanbackFeature;
    private boolean mHasLeanbackLauncher;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("uses-feature", "category");
    }

    @Override
    public void beforeCheckFile(Context context) {
        mHasLeanbackFeature = false;
        mHasLeanbackLauncher = false;
    }

    @Override
    public void afterCheckFile(Context context) {
        if (!mHasLeanbackFeature && mHasLeanbackLauncher) {
            XmlContext xmlContext = (XmlContext) context;
            org.w3c.dom.Element root = xmlContext.document.getDocumentElement();
            if (root != null) {
                xmlContext.report(
                        ISSUE,
                        root,
                        xmlContext.getLocation(root),
                        "Missing `android.software.leanback` uses-feature declaration required"
                                + " for Android TV support.");
            }
        }
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        String tag = element.getTagName();
        if ("uses-feature".equals(tag)) {
            String name = element.getAttributeNS(ANDROID_URI, "name");
            if (LEANBACK_FEATURE.equals(name)) {
                mHasLeanbackFeature = true;
            }
        } else if ("category".equals(tag)) {
            String name = element.getAttributeNS(ANDROID_URI, "name");
            if (LEANBACK_LAUNCHER.equals(name)) {
                mHasLeanbackLauncher = true;
            }
        }
    }
}
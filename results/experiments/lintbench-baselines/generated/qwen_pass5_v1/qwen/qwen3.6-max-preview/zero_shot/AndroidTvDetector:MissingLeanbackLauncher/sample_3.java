package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;
import java.util.Arrays;
import java.util.Collection;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
        "MissingLeanbackLauncher",
        "Missing Leanback Launcher Intent Filter",
        "An application intended to run on TV devices must declare a launcher activity for TV in its manifest using a `android.intent.category.LEANBACK_LAUNCHER` intent filter.",
        Category.CORRECTNESS,
        6,
        Severity.ERROR,
        new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String LEANBACK_FEATURE = "android.software.leanback";
    private static final String LEANBACK_LAUNCHER_CATEGORY = "android.intent.category.LEANBACK_LAUNCHER";

    private boolean mHasLeanbackFeature;
    private boolean mHasLeanbackLauncher;
    private Element mFeatureElement;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("uses-feature", "category");
    }

    @Override
    public void beforeCheckFile(Context context) {
        mHasLeanbackFeature = false;
        mHasLeanbackLauncher = false;
        mFeatureElement = null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();
        String name = element.getAttributeNS(ANDROID_URI, "name");

        if ("uses-feature".equals(tag) && LEANBACK_FEATURE.equals(name)) {
            mHasLeanbackFeature = true;
            mFeatureElement = element;
        } else if ("category".equals(tag) && LEANBACK_LAUNCHER_CATEGORY.equals(name)) {
            mHasLeanbackLauncher = true;
        }
    }

    @Override
    public void afterCheckFile(Context context) {
        if (mHasLeanbackFeature && !mHasLeanbackLauncher && mFeatureElement != null) {
            XmlContext xmlContext = (XmlContext) context;
            xmlContext.report(ISSUE, mFeatureElement, xmlContext.getLocation(mFeatureElement),
                "The manifest declares the `android.software.leanback` feature, but does not declare an activity with the `android.intent.category.LEANBACK_LAUNCHER` intent filter.");
        }
    }
}
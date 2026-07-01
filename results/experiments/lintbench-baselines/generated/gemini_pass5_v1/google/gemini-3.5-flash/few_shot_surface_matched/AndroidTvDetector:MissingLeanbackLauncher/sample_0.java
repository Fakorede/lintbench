package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "MissingLeanbackLauncher",
                    "Missing Leanback Launcher Intent Filter",
                    "An application intended to run on TV devices must declare a launcher "
                            + "activity for TV in its manifest using a "
                            + "`android.intent.category.LEANBACK_LAUNCHER` intent filter.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(
                            AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private boolean mDeclaresTvFeature;
    private boolean mDeclaresLeanbackLauncher;
    private Element mTvFeatureElement;
    private Element mManifestElement;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("manifest", "uses-feature", "category");
    }

    @Override
    public void beforeCheckFile(XmlContext context) {
        mDeclaresTvFeature = false;
        mDeclaresLeanbackLauncher = false;
        mTvFeatureElement = null;
        mManifestElement = null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tagName = element.getTagName();
        if ("manifest".equals(tagName)) {
            mManifestElement = element;
        } else if ("uses-feature".equals(tagName)) {
            String name = element.getAttributeNS(ANDROID_URI, "name");
            if ("android.software.leanback".equals(name)
                    || "android.hardware.type.television".equals(name)) {
                mDeclaresTvFeature = true;
                mTvFeatureElement = element;
            }
        } else if ("category".equals(tagName)) {
            String name = element.getAttributeNS(ANDROID_URI, "name");
            if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                mDeclaresLeanbackLauncher = true;
            }
        }
    }

    @Override
    public void afterCheckFile(XmlContext context) {
        if (mDeclaresTvFeature && !mDeclaresLeanbackLauncher) {
            Element targetElement = mTvFeatureElement != null ? mTvFeatureElement : mManifestElement;
            if (targetElement != null) {
                context.report(
                        ISSUE,
                        targetElement,
                        context.getLocation(targetElement),
                        "An application intended to run on TV devices must declare a launcher "
                                + "activity for TV in its manifest using a "
                                + "`android.intent.category.LEANBACK_LAUNCHER` intent filter.");
            }
        }
    }
}
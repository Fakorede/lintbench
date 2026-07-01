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
import java.util.Arrays;
import java.util.Collection;

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
    private static final String ATTR_NAME = "name";

    private boolean mHasLeanbackLauncher;
    private boolean mHasTvHardware;
    private boolean mHasLeanbackFeature;
    private Element mApplicationElement;
    private Element mManifestElement;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("manifest", "application", "uses-feature", "category");
    }

    @Override
    public void beforeCheckFile(XmlContext context) {
        mHasLeanbackLauncher = false;
        mHasTvHardware = false;
        mHasLeanbackFeature = false;
        mApplicationElement = null;
        mManifestElement = null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tagName = element.getTagName();
        if ("manifest".equals(tagName)) {
            mManifestElement = element;
        } else if ("application".equals(tagName)) {
            mApplicationElement = element;
        } else if ("uses-feature".equals(tagName)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if ("android.hardware.type.television".equals(name)) {
                mHasTvHardware = true;
            } else if ("android.software.leanback".equals(name)) {
                mHasLeanbackFeature = true;
            }
        } else if ("category".equals(tagName)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                mHasLeanbackLauncher = true;
            }
        }
    }

    @Override
    public void afterCheckFile(XmlContext context) {
        if ((mHasTvHardware || mHasLeanbackFeature) && !mHasLeanbackLauncher) {
            Element reportElement = mApplicationElement != null ? mApplicationElement : mManifestElement;
            if (reportElement != null) {
                context.report(
                        ISSUE,
                        reportElement,
                        context.getNameLocation(reportElement),
                        "An application intended to run on TV devices must declare a launcher "
                                + "activity for TV in its manifest using a "
                                + "`android.intent.category.LEANBACK_LAUNCHER` intent filter.");
            }
        }
    }
}
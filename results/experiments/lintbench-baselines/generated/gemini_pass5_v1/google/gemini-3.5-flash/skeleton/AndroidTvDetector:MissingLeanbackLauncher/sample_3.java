package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "MissingLeanbackLauncher",
                    "Missing Leanback Launcher Intent Filter",
                    "An application intended to run on TV devices must declare a launcher "
                            + "activity for TV in its manifest using a "
                            + "`android.intent.category.LEANBACK_LAUNCHER` intent filter.",
                    Category.CORRECTNESS,
                    8,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private boolean mHasLeanbackFeature = false;
    private boolean mHasTvFeature = false;
    private boolean mHasLeanbackLauncher = false;
    private Element mManifestElement = null;
    private Element mLeanbackFeatureElement = null;
    private Element mTvFeatureElement = null;
    private XmlContext mXmlContext = null;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("manifest", "uses-feature", "category");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mHasLeanbackFeature = false;
        mHasTvFeature = false;
        mHasLeanbackLauncher = false;
        mManifestElement = null;
        mLeanbackFeatureElement = null;
        mTvFeatureElement = null;
        mXmlContext = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        mXmlContext = context;
        String tagName = element.getTagName();
        if ("manifest".equals(tagName)) {
            mManifestElement = element;
        } else if ("uses-feature".equals(tagName)) {
            String name = element.getAttributeNS(ANDROID_URI, "name");
            if ("android.software.leanback".equals(name)) {
                mHasLeanbackFeature = true;
                mLeanbackFeatureElement = element;
            } else if ("android.hardware.type.television".equals(name)) {
                mHasTvFeature = true;
                mTvFeatureElement = element;
            }
        } else if ("category".equals(tagName)) {
            String name = element.getAttributeNS(ANDROID_URI, "name");
            if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                mHasLeanbackLauncher = true;
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if ((mHasLeanbackFeature || mHasTvFeature) && !mHasLeanbackLauncher) {
            Element elementToReport = null;
            if (mLeanbackFeatureElement != null) {
                elementToReport = mLeanbackFeatureElement;
            } else if (mTvFeatureElement != null) {
                elementToReport = mTvFeatureElement;
            } else if (mManifestElement != null) {
                elementToReport = mManifestElement;
            }

            if (elementToReport != null && mXmlContext != null) {
                Location location = mXmlContext.getNameLocation(elementToReport);
                mXmlContext.report(
                        ISSUE,
                        elementToReport,
                        location,
                        "An application intended to run on TV devices must declare a launcher "
                                + "activity for TV in its manifest using a "
                                + "`android.intent.category.LEANBACK_LAUNCHER` intent filter.");
            }
        }
    }
}
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
import java.util.Arrays;
import java.util.Collection;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "MissingLeanbackLauncher",
                    "Missing Leanback Launcher Intent Filter",
                    "An application intended to run on TV devices must declare a launcher activity "
                            + "for TV in its manifest using a `android.intent.category.LEANBACK_LAUNCHER` "
                            + "intent filter.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(
                            AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_NAME = "name";

    private boolean mUsesLeanback;
    private boolean mHasLeanbackLauncher;
    private Element mUsesFeatureElement;
    private Element mManifestElement;

    @Override
    public void beforeCheckFile(@NonNull XmlContext context) {
        mUsesLeanback = false;
        mHasLeanbackLauncher = false;
        mUsesFeatureElement = null;
        mManifestElement = null;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("manifest", "uses-feature", "category");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if ("manifest".equals(tagName)) {
            mManifestElement = element;
        } else if ("uses-feature".equals(tagName)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if ("android.software.leanback".equals(name) || "android.hardware.type.television".equals(name)) {
                mUsesLeanback = true;
                if (mUsesFeatureElement == null) {
                    mUsesFeatureElement = element;
                }
            }
        } else if ("category".equals(tagName)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                mHasLeanbackLauncher = true;
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull XmlContext context) {
        if (mUsesLeanback && !mHasLeanbackLauncher) {
            Element locationElement = mUsesFeatureElement != null ? mUsesFeatureElement : mManifestElement;
            if (locationElement != null) {
                context.report(
                        ISSUE,
                        locationElement,
                        context.getLocation(locationElement),
                        "An application intended to run on TV devices must declare a launcher activity "
                                + "for TV in its manifest using a `android.intent.category.LEANBACK_LAUNCHER` "
                                + "intent filter.");
            }
        }
    }
}
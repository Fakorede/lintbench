package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
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

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "MissingLeanbackLauncher",
                    "Missing Leanback Launcher Intent Filter",
                    "An application intended to run on TV devices must declare a launcher activity "
                            + "for TV in its manifest using a `android.intent.category.LEANBACK_LAUNCHER` "
                            + "intent filter.",
                    Category.CORRECTNESS,
                    8,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean mHasLeanbackFeature;
    private boolean mHasLeanbackLauncher;
    private Element mLeanbackFeatureElement;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("uses-feature", "category");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mHasLeanbackFeature = false;
        mHasLeanbackLauncher = false;
        mLeanbackFeatureElement = null;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mHasLeanbackFeature && !mHasLeanbackLauncher && mLeanbackFeatureElement != null) {
            if (context instanceof XmlContext) {
                XmlContext xmlContext = (XmlContext) context;
                xmlContext.report(
                        ISSUE,
                        mLeanbackFeatureElement,
                        xmlContext.getLocation(mLeanbackFeatureElement),
                        "An application intended to run on TV devices must declare a launcher activity for TV in its manifest using a `android.intent.category.LEANBACK_LAUNCHER` intent filter.");
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if ("uses-feature".equals(tagName)) {
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if ("android.software.leanback".equals(name)) {
                mHasLeanbackFeature = true;
                mLeanbackFeatureElement = element;
            }
        } else if ("category".equals(tagName)) {
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                mHasLeanbackLauncher = true;
            }
        }
    }
}
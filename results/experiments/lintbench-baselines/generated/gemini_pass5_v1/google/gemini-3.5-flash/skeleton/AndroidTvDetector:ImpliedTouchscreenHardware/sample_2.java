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

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ImpliedTouchscreenHardware",
                    "Touchscreen not optional",
                    "Apps require the `android.hardware.touchscreen` feature by default. "
                            + "If you want your app to be available on TV, you must also "
                            + "explicitly declare that a touchscreen is not required as follows: "
                            + "`<uses-feature android:name=\"android.hardware.touchscreen\" android:required=\"false\"/>`",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean mHasLeanbackFeature;
    private boolean mHasLeanbackLauncher;
    private boolean mHasTouchscreenRequiredFalse;
    private Element mLeanbackFeatureElement;
    private Element mLeanbackLauncherElement;
    private Element mManifestElement;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("uses-feature", "category", "manifest");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mHasLeanbackFeature = false;
        mHasLeanbackLauncher = false;
        mHasTouchscreenRequiredFalse = false;
        mLeanbackFeatureElement = null;
        mLeanbackLauncherElement = null;
        mManifestElement = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if ("manifest".equals(tagName)) {
            mManifestElement = element;
        } else if ("uses-feature".equals(tagName)) {
            String name = element.getAttributeNS(ANDROID_URI, "name");
            if ("android.software.leanback".equals(name)) {
                mHasLeanbackFeature = true;
                mLeanbackFeatureElement = element;
            } else if ("android.hardware.touchscreen".equals(name)) {
                String required = element.getAttributeNS(ANDROID_URI, "required");
                if ("false".equals(required)) {
                    mHasTouchscreenRequiredFalse = true;
                }
            }
        } else if ("category".equals(tagName)) {
            String name = element.getAttributeNS(ANDROID_URI, "name");
            if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                mHasLeanbackLauncher = true;
                mLeanbackLauncherElement = element;
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            if ((mHasLeanbackFeature || mHasLeanbackLauncher) && !mHasTouchscreenRequiredFalse) {
                Element target = mLeanbackFeatureElement;
                if (target == null) {
                    target = mLeanbackLauncherElement;
                }
                if (target == null) {
                    target = mManifestElement;
                }
                if (target != null) {
                    Location location = xmlContext.getLocation(target);
                    xmlContext.report(
                            ISSUE,
                            target,
                            location,
                            "An app that declares leanback support must also declare that touchscreen is not required: `<uses-feature android:name=\"android.hardware.touchscreen\" android:required=\"false\" />`"
                    );
                }
            }
        }
    }
}
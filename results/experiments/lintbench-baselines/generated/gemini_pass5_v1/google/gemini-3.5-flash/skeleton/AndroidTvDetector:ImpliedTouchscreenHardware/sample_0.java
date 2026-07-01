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
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ImpliedTouchscreenHardware",
                    "Touchscreen not optional",
                    "An application that is intended for Android TV must explicitly declare " +
                    "that the touchscreen hardware is not required by setting " +
                    "`<uses-feature android:name=\"android.hardware.touchscreen\" android:required=\"false\"/>`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean mHasLeanback;
    private boolean mHasTouchscreenNotRequired;
    private Element mLeanbackElement;
    private Element mTvLauncherElement;
    private Element mManifestElement;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("manifest", "uses-feature", "category");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mHasLeanback = false;
        mHasTouchscreenNotRequired = false;
        mLeanbackElement = null;
        mTvLauncherElement = null;
        mManifestElement = null;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mHasLeanback && !mHasTouchscreenNotRequired) {
            Element targetElement = mTvLauncherElement;
            if (targetElement == null) {
                targetElement = mLeanbackElement;
            }
            if (targetElement == null) {
                targetElement = mManifestElement;
            }

            if (targetElement != null && context instanceof XmlContext) {
                XmlContext xmlContext = (XmlContext) context;
                xmlContext.report(
                        ISSUE,
                        targetElement,
                        xmlContext.getLocation(targetElement),
                        "Hardware feature `android.hardware.touchscreen` should be declared as " +
                        "not required for TV-ready applications.");
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if ("manifest".equals(tagName)) {
            mManifestElement = element;
        } else if ("uses-feature".equals(tagName)) {
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if ("android.software.leanback".equals(name)) {
                mHasLeanback = true;
                mLeanbackElement = element;
            } else if ("android.hardware.touchscreen".equals(name)) {
                String required = element.getAttributeNS("http://schemas.android.com/apk/res/android", "required");
                if ("false".equals(required)) {
                    mHasTouchscreenNotRequired = true;
                }
            }
        } else if ("category".equals(tagName)) {
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                mHasLeanback = true;
                mTvLauncherElement = element;
            }
        }
    }
}
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
            new Implementation(AndroidTvDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ImpliedTouchscreenHardware",
                    "Touchscreen not optional",
                    "Apps require the `android.hardware.touchscreen` feature by default. If "
                            + "you want your app to be available on TV, you must also "
                            + "explicitly declare that a touchscreen is not required as "
                            + "follows: `<uses-feature android:name=\"android.hardware.touchscreen\" "
                            + "android:required=\"false\"/>`",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean mHasLeanbackLauncher;
    private boolean mHasLeanbackFeature;
    private boolean mHasTouchscreenFalse;
    private Element mTouchscreenElement;
    private Element mManifestElement;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("category", "uses-feature", "manifest");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mHasLeanbackLauncher = false;
        mHasLeanbackFeature = false;
        mHasTouchscreenFalse = false;
        mTouchscreenElement = null;
        mManifestElement = null;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!(context instanceof XmlContext)) {
            return;
        }
        XmlContext xmlContext = (XmlContext) context;
        boolean isTvApp = mHasLeanbackLauncher || mHasLeanbackFeature;
        if (isTvApp && !mHasTouchscreenFalse) {
            Location location;
            if (mTouchscreenElement != null) {
                location = xmlContext.getNameLocation(mTouchscreenElement);
            } else if (mManifestElement != null) {
                location = xmlContext.getNameLocation(mManifestElement);
            } else {
                location = xmlContext.getLocation(xmlContext.getDocument());
            }

            xmlContext.report(
                    ISSUE,
                    location,
                    "An Android TV application should explicitly declare that a touchscreen is not required by "
                            + "setting `<uses-feature android:name=\"android.hardware.touchscreen\" android:required=\"false\"/>`"
            );
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if ("manifest".equals(tagName)) {
            mManifestElement = element;
        } else if ("category".equals(tagName)) {
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                mHasLeanbackLauncher = true;
            }
        } else if ("uses-feature".equals(tagName)) {
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if ("android.software.leanback".equals(name)) {
                mHasLeanbackFeature = true;
            } else if ("android.hardware.touchscreen".equals(name)) {
                mTouchscreenElement = element;
                String required = element.getAttributeNS("http://schemas.android.com/apk/res/android", "required");
                if ("false".equals(required)) {
                    mHasTouchscreenFalse = true;
                }
            }
        }
    }
}
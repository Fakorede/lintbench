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
                    "ImpliedTouchscreenHardware",
                    "Hardware feature touchscreen not marked as optional",
                    "An application that should be available on Android TV must design for "
                            + "non-touchscreen devices, and explicitly declare that a touchscreen is "
                            + "not required by setting `<uses-feature android:name=\"android.hardware.touchscreen\" "
                            + "android:required=\"false\" />`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private boolean mHasLeanback;
    private boolean mHasTouchscreenFeature;
    private boolean mTouchscreenRequired;
    private Element mTouchscreenElement;
    private Element mLeanbackElement;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("uses-feature", "category");
    }

    @Override
    public void beforeCheckFile(@NonNull XmlContext context) {
        mHasLeanback = false;
        mHasTouchscreenFeature = false;
        mTouchscreenRequired = true;
        mTouchscreenElement = null;
        mLeanbackElement = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if (tagName.equals("uses-feature")) {
            String name = element.getAttributeNS(com.android.SdkConstants.ANDROID_URI, "name");
            if ("android.software.leanback".equals(name)) {
                mHasLeanback = true;
                if (mLeanbackElement == null) {
                    mLeanbackElement = element;
                }
            } else if ("android.hardware.touchscreen".equals(name)) {
                mHasTouchscreenFeature = true;
                mTouchscreenElement = element;
                String required = element.getAttributeNS(com.android.SdkConstants.ANDROID_URI, "required");
                mTouchscreenRequired = !"false".equals(required);
            }
        } else if (tagName.equals("category")) {
            String name = element.getAttributeNS(com.android.SdkConstants.ANDROID_URI, "name");
            if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                mHasLeanback = true;
                if (mLeanbackElement == null) {
                    mLeanbackElement = element;
                }
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull XmlContext context) {
        if (mHasLeanback) {
            if (!mHasTouchscreenFeature || mTouchscreenRequired) {
                if (mTouchscreenElement != null) {
                    context.report(
                            ISSUE,
                            mTouchscreenElement,
                            context.getLocation(mTouchscreenElement),
                            "Touchscreen is required but the device is declared as supporting Leanback");
                } else if (mLeanbackElement != null) {
                    context.report(
                            ISSUE,
                            mLeanbackElement,
                            context.getLocation(mLeanbackElement),
                            "The manifest specifies TV support but does not declare that "
                                    + "a touchscreen is not required. Add "
                                    + "`<uses-feature android:name=\"android.hardware.touchscreen\" "
                                    + "android:required=\"false\" />` to the manifest.");
                }
            }
        }
    }
}
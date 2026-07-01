package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_REQUIRED;
import static com.android.xml.AndroidManifest.NODE_USES_FEATURE;

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

import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String HARDWARE_FEATURE_TOUCHSCREEN = "android.hardware.touchscreen";
    private static final String USES_FEATURE_TV = "android.software.leanback";

    public static final Issue IMPLIED_TOUCHSCREEN_HARDWARE =
            Issue.create(
                    "ImpliedTouchscreenHardware",
                    "Touchscreen not optional",
                    "Apps require the `android.hardware.touchscreen` feature by default. "
                            + "If you want your app to be available on TV, you must also "
                            + "explicitly declare that a touchscreen is not required as follows:\n"
                            + "`<uses-feature android:name=\"android.hardware.touchscreen\" "
                            + "android:required=\"false\"/>`",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    /** Whether the manifest targets TV (has leanback uses-feature) */
    private boolean mTargetsTv;

    /** Whether the manifest explicitly declares touchscreen as not required */
    private boolean mTouchscreenDeclaredNotRequired;

    /** The element to report the issue on (the leanback uses-feature element) */
    private Element mLeanbackElement;

    /** The context for reporting */
    private XmlContext mContext;

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(NODE_USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mTargetsTv = false;
        mTouchscreenDeclaredNotRequired = false;
        mLeanbackElement = null;
        mContext = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        mContext = context;

        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (name == null) {
            return;
        }

        if (USES_FEATURE_TV.equals(name)) {
            // Check if this is required (or required is not explicitly set to false)
            String required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
            // If required is not "false", the app targets TV
            if (!"false".equals(required)) {
                mTargetsTv = true;
                mLeanbackElement = element;
            }
        } else if (HARDWARE_FEATURE_TOUCHSCREEN.equals(name)) {
            // Check if required is explicitly set to false
            String required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
            if ("false".equals(required)) {
                mTouchscreenDeclaredNotRequired = true;
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mTargetsTv && !mTouchscreenDeclaredNotRequired && mLeanbackElement != null && mContext != null) {
            mContext.report(
                    IMPLIED_TOUCHSCREEN_HARDWARE,
                    mLeanbackElement,
                    mContext.getLocation(mLeanbackElement),
                    "You must explicitly declare that touchscreen is not required for TV apps.\n"
                            + "Add `<uses-feature android:name=\"android.hardware.touchscreen\" "
                            + "android:required=\"false\"/>` to your manifest.");
        }
    }
}
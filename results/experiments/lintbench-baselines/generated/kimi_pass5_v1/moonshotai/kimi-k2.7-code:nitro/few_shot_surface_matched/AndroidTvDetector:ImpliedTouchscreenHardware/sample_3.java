package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ImpliedTouchscreenHardware",
                    "Touchscreen hardware is implied",
                    "Apps require the android.hardware.touchscreen feature by default. If you want"
                            + " your app to be available on TV, you must also explicitly declare"
                            + " that a touchscreen is not required as follows: <uses-feature"
                            + " android:name=\"android.hardware.touchscreen\""
                            + " android:required=\"false\"/>",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String USES_FEATURE = "uses-feature";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_REQUIRED = "required";
    private static final String VALUE_FALSE = "false";
    private static final String TOUCHSCREEN_HARDWARE = "android.hardware.touchscreen";
    private static final String TV_HARDWARE = "android.hardware.type.television";
    private static final String LEANBACK = "android.software.leanback";

    private boolean mHasTvFeature;
    private boolean mTouchscreenNotRequired;
    private org.w3c.dom.Element mTvFeatureElement;

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(Context context) {
        mHasTvFeature = false;
        mTouchscreenNotRequired = false;
        mTvFeatureElement = null;
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (TV_HARDWARE.equals(name) || LEANBACK.equals(name)) {
            mHasTvFeature = true;
            mTvFeatureElement = element;
        } else if (TOUCHSCREEN_HARDWARE.equals(name)) {
            String required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
            if (VALUE_FALSE.equals(required)) {
                mTouchscreenNotRequired = true;
            }
        }
    }

    @Override
    public void afterCheckFile(Context context) {
        if (mHasTvFeature && !mTouchscreenNotRequired && mTvFeatureElement != null) {
            context.report(
                    ISSUE,
                    mTvFeatureElement,
                    context.getLocation(mTvFeatureElement),
                    "Declare that this app does not require a touchscreen by adding"
                            + " android:required=\"false\" to a <uses-feature> tag for"
                            + " android.hardware.touchscreen");
        }
    }
}
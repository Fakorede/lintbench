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
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_REQUIRED = "required";
    private static final String ANDROID_HARDWARE_TOUCHSCREEN = "android.hardware.touchscreen";
    private static final String ANDROID_HARDWARE_TYPE_TELEVISION =
            "android.hardware.type.television";
    private static final String ANDROID_SOFTWARE_LEANBACK = "android.software.leanback";

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ImpliedTouchscreenHardware",
                    "Touchscreen not optional",
                    "Apps require the `android.hardware.touchscreen` feature by default. "
                            + "If you want your app to be available on TV, you must also "
                            + "explicitly declare that a touchscreen is not required as follows: "
                            + "`<uses-feature android:name=\"android.hardware.touchscreen\" "
                            + "android:required=\"false\"/>`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean mHasTelevisionFeature;
    private boolean mHasTouchscreenOptional;
    private Element mTelevisionFeatureElement;

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mHasTelevisionFeature = false;
        mHasTouchscreenOptional = false;
        mTelevisionFeatureElement = null;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mHasTelevisionFeature && !mHasTouchscreenOptional) {
            XmlContext xmlContext = (XmlContext) context;
            xmlContext.report(
                    ISSUE,
                    mTelevisionFeatureElement,
                    xmlContext.getElementLocation(mTelevisionFeatureElement),
                    "This TV app must explicitly declare that a touchscreen is not required; "
                            + "add `<uses-feature android:name=\"android.hardware.touchscreen\" "
                            + "android:required=\"false\"/>` to the manifest.");
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (ANDROID_HARDWARE_TYPE_TELEVISION.equals(name)
                || ANDROID_SOFTWARE_LEANBACK.equals(name)) {
            mHasTelevisionFeature = true;
            if (mTelevisionFeatureElement == null) {
                mTelevisionFeatureElement = element;
            }
        } else if (ANDROID_HARDWARE_TOUCHSCREEN.equals(name)) {
            String required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
            if ("false".equals(required)) {
                mHasTouchscreenOptional = true;
            }
        }
    }
}
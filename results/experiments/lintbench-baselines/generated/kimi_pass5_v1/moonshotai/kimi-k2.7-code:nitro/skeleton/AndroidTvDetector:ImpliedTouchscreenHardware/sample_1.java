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

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String USES_FEATURE_TAG = "uses-feature";
    private static final String NAME_ATTRIBUTE = "name";
    private static final String REQUIRED_ATTRIBUTE = "required";
    private static final String HARDWARE_TOUCHSCREEN = "android.hardware.touchscreen";
    private static final String HARDWARE_TYPE_TELEVISION = "android.hardware.type.television";
    private static final String SOFTWARE_LEANBACK = "android.software.leanback";

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ImpliedTouchscreenHardware",
                    "Touchscreen not optional",
                    "Apps require the `android.hardware.touchscreen` feature by default. If you want your app to be available on TV, you must also explicitly declare that a touchscreen is not required using `<uses-feature android:name=\"android.hardware.touchscreen\" android:required=\"false\"/>`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean mHasTvFeature;
    private boolean mHasTouchscreenNotRequired;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(USES_FEATURE_TAG);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mHasTvFeature = false;
        mHasTouchscreenNotRequired = false;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mHasTvFeature && !mHasTouchscreenNotRequired) {
            Element root = context.getDocument().getDocumentElement();
            if (root != null) {
                XmlContext xmlContext = (XmlContext) context;
                xmlContext.report(
                        ISSUE,
                        root,
                        xmlContext.getNameLocation(root),
                        "Touchscreen not optional: declare `<uses-feature android:name=\"android.hardware.touchscreen\" android:required=\"false\"/>` when supporting TV");
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(ANDROID_URI, NAME_ATTRIBUTE);
        if (HARDWARE_TOUCHSCREEN.equals(name)) {
            String required = element.getAttributeNS(ANDROID_URI, REQUIRED_ATTRIBUTE);
            if ("false".equalsIgnoreCase(required)) {
                mHasTouchscreenNotRequired = true;
            }
        } else if (HARDWARE_TYPE_TELEVISION.equals(name) || SOFTWARE_LEANBACK.equals(name)) {
            mHasTvFeature = true;
        }
    }
}
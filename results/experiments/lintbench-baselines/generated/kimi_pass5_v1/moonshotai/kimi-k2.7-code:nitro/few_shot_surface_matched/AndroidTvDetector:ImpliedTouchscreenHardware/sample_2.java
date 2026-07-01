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
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ImpliedTouchscreenHardware",
                    "Touchscreen hardware is implicitly required",
                    "Apps require the `android.hardware.touchscreen` feature by default. If your"
                            + " app is meant to be available on Android TV devices, you must"
                            + " explicitly declare that a touchscreen is not required by adding"
                            + " `<uses-feature android:name=\"android.hardware.touchscreen\""
                            + " android:required=\"false\"/>`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private static final String HARDWARE_TOUCHSCREEN = "android.hardware.touchscreen";
    private static final String SOFTWARE_LEANBACK = "android.software.leanback";

    private boolean mHasLeanback;
    private boolean mTouchscreenNotRequired;
    private Element mLeanbackElement;

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(NODE_USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mHasLeanback = false;
        mTouchscreenNotRequired = false;
        mLeanbackElement = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        if (HARDWARE_TOUCHSCREEN.equals(name)) {
            String required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
            if ("false".equals(required)) {
                mTouchscreenNotRequired = true;
            }
        } else if (SOFTWARE_LEANBACK.equals(name)) {
            mHasLeanback = true;
            mLeanbackElement = element;
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mHasLeanback && !mTouchscreenNotRequired && mLeanbackElement != null) {
            XmlContext xmlContext = (XmlContext) context;
            xmlContext.report(
                    ISSUE,
                    mLeanbackElement,
                    xmlContext.getLocation(mLeanbackElement),
                    "Apps targeting Android TV must explicitly declare that a touchscreen is not"
                            + " required; add `<uses-feature"
                            + " android:name=\"android.hardware.touchscreen\""
                            + " android:required=\"false\"/>`");
        }
    }
}
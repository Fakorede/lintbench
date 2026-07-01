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

    private static final String HARDWARE_FEATURE_TOUCHSCREEN = "android.hardware.touchscreen";

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
                    7,
                    Severity.ERROR,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    /** Whether the manifest explicitly declares touchscreen as not required */
    private boolean mTouchscreenDeclaredNotRequired;

    /** Whether the manifest targets TV (uses leanback feature) */
    private boolean mTargetsTv;

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mTouchscreenDeclaredNotRequired = false;
        mTargetsTv = false;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mTargetsTv && !mTouchscreenDeclaredNotRequired) {
            XmlContext xmlContext = (XmlContext) context;
            xmlContext.report(
                    IMPLIED_TOUCHSCREEN_HARDWARE,
                    xmlContext.getDocument().getDocumentElement(),
                    xmlContext.getLocation(xmlContext.getDocument().getDocumentElement()),
                    "You must explicitly declare that the `android.hardware.touchscreen` "
                            + "feature is not required in order for your app to be available "
                            + "on TV. Use "
                            + "`<uses-feature android:name=\"android.hardware.touchscreen\" "
                            + "android:required=\"false\"/>`.");
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(NODE_USES_FEATURE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (name == null) {
            return;
        }

        if ("android.hardware.type.television".equals(name)
                || "android.software.leanback".equals(name)) {
            // Check if required is not explicitly set to false
            String required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
            if (required == null || !required.equals("false")) {
                mTargetsTv = true;
            } else {
                // Even if required=false, the app may still target TV
                mTargetsTv = true;
            }
        }

        if (HARDWARE_FEATURE_TOUCHSCREEN.equals(name)) {
            String required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
            if ("false".equals(required)) {
                mTouchscreenDeclaredNotRequired = true;
            }
        }
    }
}
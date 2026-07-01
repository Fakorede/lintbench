package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_REQUIRED;
import static com.android.xml.AndroidManifest.NODE_USES_FEATURE;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
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
                    8,
                    Severity.ERROR,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    /** Whether we have seen a uses-feature element for touchscreen with required="false" */
    private boolean mFoundExplicitTouchscreenNotRequired;

    /** Whether the manifest targets TV (uses android.software.leanback) */
    private boolean mTargetsTv;

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(NODE_USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(@NonNull com.android.tools.lint.detector.api.Context context) {
        mFoundExplicitTouchscreenNotRequired = false;
        mTargetsTv = false;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (name == null) {
            return;
        }

        if (HARDWARE_FEATURE_TOUCHSCREEN.equals(name)) {
            String required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
            if ("false".equals(required)) {
                mFoundExplicitTouchscreenNotRequired = true;
            }
        } else if ("android.software.leanback".equals(name)) {
            mTargetsTv = true;
        }
    }

    @Override
    public void afterCheckFile(@NonNull com.android.tools.lint.detector.api.Context context) {
        if (mTargetsTv && !mFoundExplicitTouchscreenNotRequired) {
            // Find the manifest element to report the error on
            if (context instanceof XmlContext) {
                XmlContext xmlContext = (XmlContext) context;
                org.w3c.dom.Document document = xmlContext.document;
                if (document != null) {
                    Element manifestElement = document.getDocumentElement();
                    if (manifestElement != null) {
                        xmlContext.report(
                                IMPLIED_TOUCHSCREEN_HARDWARE,
                                manifestElement,
                                xmlContext.getLocation(manifestElement),
                                "You must explicitly declare that the `android.hardware.touchscreen` "
                                        + "feature is not required using "
                                        + "`<uses-feature android:name=\"android.hardware.touchscreen\" "
                                        + "android:required=\"false\"/>` if you want your app to be "
                                        + "available on TV.");
                    }
                }
            }
        }
    }
}
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

    /** Whether we have seen an explicit touchscreen uses-feature element */
    private boolean mSeenTouchscreenFeature;

    /** Whether this manifest targets TV (has a uses-feature for leanback) */
    private boolean mSeenLeanbackRequirement;

    /** The manifest element to report the issue on, if needed */
    private Element mManifestElement;

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mSeenTouchscreenFeature = false;
        mSeenLeanbackRequirement = false;
        mManifestElement = null;
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

        if (HARDWARE_FEATURE_TOUCHSCREEN.equals(name)) {
            // Check if required="false" is set
            String required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
            if ("false".equals(required)) {
                mSeenTouchscreenFeature = true;
            }
        }

        if ("android.software.leanback".equals(name)) {
            mSeenLeanbackRequirement = true;
            // Store the element's parent (manifest) or the element itself for reporting
            if (mManifestElement == null) {
                mManifestElement = element;
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mSeenLeanbackRequirement && !mSeenTouchscreenFeature) {
            XmlContext xmlContext = (XmlContext) context;
            Element reportElement = mManifestElement;
            if (reportElement != null) {
                xmlContext.report(
                        IMPLIED_TOUCHSCREEN_HARDWARE,
                        reportElement,
                        xmlContext.getLocation(reportElement),
                        "You must explicitly declare that a touchscreen is not required as "
                                + "follows: "
                                + "`<uses-feature android:name=\"android.hardware.touchscreen\" "
                                + "android:required=\"false\"/>`");
            }
        }
    }
}
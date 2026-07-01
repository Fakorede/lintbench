package com.android.tools.lint.checks;

import com.android.SdkConstants;
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
import java.util.HashSet;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "UnsupportedTvHardware",
            "Unsupported TV Hardware Feature",
            "The `<uses-feature>` element should not require this unsupported TV "
                    + "hardware feature. Any uses-feature not explicitly marked with "
                    + "`required=\"false\"` is necessary on the device to be installed "
                    + "on. Ensure that any features that might prevent it from being "
                    + "installed on a TV device are reviewed and marked as not "
                    + "required in the manifest.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE)
    );

    private static final Set<String> UNSUPPORTED_FEATURES = new HashSet<>();

    static {
        UNSUPPORTED_FEATURES.add("android.hardware.touchscreen");
        UNSUPPORTED_FEATURES.add("android.hardware.touchscreen.multitouch");
        UNSUPPORTED_FEATURES.add("android.hardware.touchscreen.multitouch.distinct");
        UNSUPPORTED_FEATURES.add("android.hardware.touchscreen.multitouch.jazzhand");
        UNSUPPORTED_FEATURES.add("android.hardware.camera");
        UNSUPPORTED_FEATURES.add("android.hardware.camera.autofocus");
        UNSUPPORTED_FEATURES.add("android.hardware.camera.front");
        UNSUPPORTED_FEATURES.add("android.hardware.camera.flash");
        UNSUPPORTED_FEATURES.add("android.hardware.camera.level.full");
        UNSUPPORTED_FEATURES.add("android.hardware.camera.capability.manual_sensor");
        UNSUPPORTED_FEATURES.add("android.hardware.camera.capability.manual_post_processing");
        UNSUPPORTED_FEATURES.add("android.hardware.camera.capability.raw");
        UNSUPPORTED_FEATURES.add("android.hardware.telephony");
        UNSUPPORTED_FEATURES.add("android.hardware.telephony.cdma");
        UNSUPPORTED_FEATURES.add("android.hardware.telephony.gsm");
        UNSUPPORTED_FEATURES.add("android.hardware.nfc");
        UNSUPPORTED_FEATURES.add("android.hardware.nfc.hce");
        UNSUPPORTED_FEATURES.add("android.hardware.location.gps");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_USES_FEATURE);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Attr nameAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
        if (nameAttr == null) {
            return;
        }

        String featureName = nameAttr.getValue();
        if (UNSUPPORTED_FEATURES.contains(featureName)) {
            Attr requiredAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_REQUIRED);
            boolean isRequired = true;
            if (requiredAttr != null) {
                isRequired = Boolean.parseBoolean(requiredAttr.getValue());
            }

            if (isRequired) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(nameAttr),
                        "Expect `android:required=\"false\"` for TV-unsupported feature `" + featureName + "`"
                );
            }
        }
    }
}
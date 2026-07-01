package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "UnsupportedTvHardware",
            "Unsupported TV Hardware Feature",
            "The `<uses-feature>` element should not require this unsupported TV " +
            "hardware feature. Any uses-feature not explicitly marked with " +
            "`required=\"false\"` is necessary on the device to be installed " +
            "on. Ensure that any features that might prevent it from being " +
            "installed on a TV device are reviewed and marked as not " +
            "required in the manifest.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    AndroidTvDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    );

    private static final Set<String> UNSUPPORTED_FEATURES = new HashSet<>(Arrays.asList(
            "android.hardware.touchscreen",
            "android.hardware.touchscreen.multitouch",
            "android.hardware.touchscreen.multitouch.distinct",
            "android.hardware.touchscreen.multitouch.jazzhand",
            "android.hardware.camera",
            "android.hardware.camera.autofocus",
            "android.hardware.camera.flash",
            "android.hardware.camera.front",
            "android.hardware.camera.any",
            "android.hardware.telephony",
            "android.hardware.telephony.cdma",
            "android.hardware.telephony.gsm",
            "android.hardware.nfc",
            "android.hardware.nfc.hce",
            "android.hardware.location.gps",
            "android.hardware.sensor.barometer",
            "android.hardware.sensor.gyroscope"
    ));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_USES_FEATURE);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String featureName = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
        if (featureName.isEmpty()) {
            return;
        }

        if (UNSUPPORTED_FEATURES.contains(featureName)) {
            String required = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_REQUIRED);
            boolean isRequired = required.isEmpty() || Boolean.parseBoolean(required);

            if (isRequired) {
                LintFix fix = fix()
                        .set(SdkConstants.ANDROID_URI, SdkConstants.ATTR_REQUIRED, "false")
                        .build();

                context.report(
                        ISSUE,
                        element,
                        context.getNameLocation(element),
                        "The unsupported TV hardware feature `" + featureName + "` should not be required",
                        fix
                );
            }
        }
    }
}
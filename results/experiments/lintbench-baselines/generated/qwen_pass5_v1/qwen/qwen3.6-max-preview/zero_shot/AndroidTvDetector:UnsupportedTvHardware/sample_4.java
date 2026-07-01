package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class AndroidTvDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "UnsupportedTvHardware",
            "Unsupported TV Hardware Feature",
            "The `<uses-feature>` element should not require this unsupported TV hardware feature. " +
            "Any uses-feature not explicitly marked with `required=\"false\"` is necessary on the device to be installed on. " +
            "Ensure that any features that might prevent it from being installed on a TV device are reviewed and marked as not required in the manifest.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE)
    );

    private static final Set<String> UNSUPPORTED_FEATURES = new HashSet<>(Arrays.asList(
            "android.hardware.touchscreen",
            "android.hardware.camera",
            "android.hardware.camera.autofocus",
            "android.hardware.camera.flash",
            "android.hardware.camera.front",
            "android.hardware.nfc",
            "android.hardware.telephony",
            "android.hardware.bluetooth",
            "android.hardware.bluetooth_le",
            "android.hardware.wifi.direct",
            "android.hardware.usb.host",
            "android.hardware.usb.accessory",
            "android.hardware.location.gps",
            "android.hardware.microphone"
    ));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("uses-feature");
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        String required = element.getAttributeNS(SdkConstants.ANDROID_URI, "required");
        if ("false".equals(required)) {
            return;
        }

        Attr nameAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "name");
        if (nameAttr == null) {
            return;
        }

        String featureName = nameAttr.getValue();
        if (featureName.isEmpty()) {
            return;
        }

        if (isUnsupportedTvFeature(featureName)) {
            context.report(ISSUE, element, context.getLocation(nameAttr),
                    "This hardware feature is not supported on Android TV. " +
                    "Add `android:required=\"false\"` to the `<uses-feature>` element.");
        }
    }

    private static boolean isUnsupportedTvFeature(@NotNull String featureName) {
        if (UNSUPPORTED_FEATURES.contains(featureName)) {
            return true;
        }
        // All sensor features are unsupported on Android TV
        return featureName.startsWith("android.hardware.sensor.");
    }
}
package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_REQUIRED;
import static com.android.SdkConstants.TAG_USES_FEATURE;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final Set<String> UNSUPPORTED_TV_FEATURES = new HashSet<>(Arrays.asList(
        "android.hardware.touchscreen",
        "android.hardware.camera",
        "android.hardware.camera.autofocus",
        "android.hardware.camera.flash",
        "android.hardware.camera.front",
        "android.hardware.camera.any",
        "android.hardware.location",
        "android.hardware.location.network",
        "android.hardware.location.gps",
        "android.hardware.microphone",
        "android.hardware.sensor",
        "android.hardware.telephony",
        "android.hardware.nfc",
        "android.hardware.bluetooth",
        "android.hardware.wifi",
        "android.hardware.usb.host",
        "android.hardware.usb.accessory",
        "android.hardware.screen.portrait",
        "android.hardware.screen.landscape"
    ));

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

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES_FEATURE);
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        String featureName = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (featureName == null || featureName.isEmpty()) {
            return;
        }

        if (!UNSUPPORTED_TV_FEATURES.contains(featureName)) {
            return;
        }

        String required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
        if ("false".equals(required)) {
            return;
        }

        context.report(ISSUE, element, context.getLocation(element),
            "This hardware feature is not supported on Android TV. Mark it as `android:required=\"false\"` to allow installation on TV devices.");
    }
}
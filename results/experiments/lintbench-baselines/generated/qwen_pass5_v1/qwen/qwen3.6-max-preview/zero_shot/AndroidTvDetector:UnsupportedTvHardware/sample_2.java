package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "UnsupportedTvHardware",
            "Unsupported TV Hardware Feature",
            "The `<uses-feature>` element should not require this unsupported TV hardware feature. " +
            "Any uses-feature not explicitly marked with `required=\"false\"` is necessary on the device " +
            "to be installed on. Ensure that any features that might prevent it from being installed on " +
            "a TV device are reviewed and marked as not required in the manifest.\n" +
            "More info: https://developer.android.com/training/tv/start/hardware.html#unsupported-features",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private static final Set<String> UNSUPPORTED_FEATURES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "android.hardware.touchscreen",
            "android.hardware.camera",
            "android.hardware.camera.autofocus",
            "android.hardware.camera.flash",
            "android.hardware.camera.front",
            "android.hardware.camera.any",
            "android.hardware.nfc",
            "android.hardware.telephony",
            "android.hardware.microphone",
            "android.hardware.bluetooth"
    )));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("uses-feature");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String featureName = element.getAttributeNS(SdkConstants.ANDROID_URI, "name");
        if (featureName == null || featureName.isEmpty() || !UNSUPPORTED_FEATURES.contains(featureName)) {
            return;
        }

        String requiredAttr = element.getAttributeNS(SdkConstants.ANDROID_URI, "required");
        boolean isRequired = requiredAttr == null || requiredAttr.isEmpty() || requiredAttr.equalsIgnoreCase("true");

        if (isRequired) {
            context.report(ISSUE, context.getLocation(element),
                    "This hardware feature is not supported on Android TV. " +
                    "Add `android:required=\"false\"` to the `<uses-feature>` element.");
        }
    }
}
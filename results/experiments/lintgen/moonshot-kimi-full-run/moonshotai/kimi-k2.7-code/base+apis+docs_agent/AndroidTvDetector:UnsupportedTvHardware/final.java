package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_REQUIRED;
import static com.android.SdkConstants.TAG_USES_FEATURE;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
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

    private static final Set<String> UNSUPPORTED_TV_FEATURES = new HashSet<>(Arrays.asList(
            "android.hardware.camera",
            "android.hardware.camera.autofocus",
            "android.hardware.camera.flash",
            "android.hardware.location",
            "android.hardware.location.gps",
            "android.hardware.location.network",
            "android.hardware.microphone",
            "android.hardware.sensor",
            "android.hardware.telephony",
            "android.hardware.touchscreen",
            "android.hardware.type.automotive",
            "android.hardware.type.television"
    ));

    public static final Issue ISSUE = Issue.create(
            "UnsupportedTvHardware",
            "Unsupported TV hardware feature",
            "The `<uses-feature>` element should not require this unsupported TV hardware feature. "
                    + "Any uses-feature not explicitly marked with `required=\"false\"` is "
                    + "necessary on the device to be installed on. Ensure that any features that "
                    + "might prevent it from being installed on a TV device are reviewed and "
                    + "marked as not required in the manifest.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE)
    );

    @Override
    @NonNull
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES_FEATURE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (name.isEmpty() || !UNSUPPORTED_TV_FEATURES.contains(name)) {
            return;
        }

        String required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
        if ("false".equals(required)) {
            return;
        }

        String message = String.format(
                "The feature \"%s\" is not supported on TV devices and should not be required. "
                        + "Mark it with `android:required=\"false\"` if it must be declared.",
                name);

        context.report(ISSUE, element, context.getLocation(element), message);
    }
}
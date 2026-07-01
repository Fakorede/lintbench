package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_NS_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_REQUIRED;
import static com.android.SdkConstants.TAG_USES_FEATURE;
import static com.android.SdkConstants.VALUE_FALSE;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    private static final Set<String> UNSUPPORTED_TV_FEATURES;
    static {
        Set<String> features = new HashSet<>();
        features.add("android.hardware.telephony");
        features.add("android.hardware.camera");
        features.add("android.hardware.nfc");
        features.add("android.hardware.location.gps");
        UNSUPPORTED_TV_FEATURES = Collections.unmodifiableSet(features);
    }

    public static final Issue UNSUPPORTED_TV_HARDWARE = Issue.create(
            "UnsupportedTvHardware",
            "Unsupported TV hardware feature",
            "The `<uses-feature>` element should not require a hardware feature that is not supported on TV devices, "
                    + "such as telephony, camera, NFC, or GPS. Any `<uses-feature>` not explicitly marked with "
                    + "`required=\"false\"` is considered necessary on the device for installation. Features that "
                    + "would prevent the app from being installed on a TV device should be marked as not required.\n"
                    + "See https://developer.android.com/training/tv/start/hardware.html#unsupported-features",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES_FEATURE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(ANDROID_NS_URI, ATTR_NAME);
        if (name == null || name.isEmpty() || !UNSUPPORTED_TV_FEATURES.contains(name)) {
            return;
        }

        String required = element.getAttributeNS(ANDROID_NS_URI, ATTR_REQUIRED);
        if (VALUE_FALSE.equalsIgnoreCase(required)) {
            return;
        }

        context.report(
                UNSUPPORTED_TV_HARDWARE,
                element,
                context.getLocation(element),
                "Unsupported TV hardware feature `" + name + "` should be marked `required=\"false\"`");
    }
}
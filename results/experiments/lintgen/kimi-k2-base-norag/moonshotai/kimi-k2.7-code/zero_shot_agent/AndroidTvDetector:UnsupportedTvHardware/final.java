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
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String UNSUPPORTED_TV_HARDWARE_URL =
            "https://developer.android.com/training/tv/start/hardware.html#unsupported-features";

    private static final Set<String> UNSUPPORTED_FEATURES;
    static {
        Set<String> set = new HashSet<>();
        set.add("android.hardware.camera");
        set.add("android.hardware.location.gps");
        set.add("android.hardware.microphone");
        set.add("android.hardware.nfc");
        set.add("android.hardware.telephony");
        set.add("android.hardware.touchscreen");
        set.add("android.hardware.faketouch");
        UNSUPPORTED_FEATURES = Collections.unmodifiableSet(set);
    }

    public static final Issue ISSUE = Issue.create(
            "UnsupportedTvHardware",
            "Unsupported TV hardware feature",
            "The `<uses-feature>` element should not require this unsupported TV hardware feature. "
                    + "Any uses-feature not explicitly marked with `required=\"false\"` is "
                    + "necessary on the device to be installed on. Ensure that any features "
                    + "that might prevent it from being installed on a TV device are reviewed "
                    + "and marked as not required in the manifest.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE)
    ).addMoreInfo(UNSUPPORTED_TV_HARDWARE_URL);

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_USES_FEATURE);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
                .trim();
        if (name.isEmpty() || !UNSUPPORTED_FEATURES.contains(name)) {
            return;
        }

        String required = element.getAttributeNS(SdkConstants.ANDROID_URI,
                SdkConstants.ATTR_REQUIRED).trim();
        if (required.isEmpty()) {
            required = SdkConstants.VALUE_TRUE;
        }

        if (!SdkConstants.VALUE_FALSE.equals(required)) {
            String message = String.format(
                    "The `<uses-feature>` element should not require the unsupported TV hardware "
                            + "feature `%1$s`. Mark it with `android:required=\"false\"` or remove "
                            + "it.",
                    name);
            context.report(ISSUE, context.getElementLocation(element), message);
        }
    }
}
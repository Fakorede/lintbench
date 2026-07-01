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
import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.Set;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    private static final String TV_HARDWARE_DOCS =
            "https://developer.android.com/training/tv/start/hardware.html#unsupported-features";

    public static final Issue ISSUE = Issue.create(
                    "UnsupportedTvHardware",
                    "Unsupported TV Hardware Feature",
                    "The `<uses-feature>` element should not require this unsupported TV hardware "
                            + "feature. Any uses-feature not explicitly marked with "
                            + "`required=\"false\"` is necessary on the device to be installed on. "
                            + "Ensure that any features that might prevent it from being installed "
                            + "on a TV device are reviewed and marked as not required in the manifest.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE))
            .addMoreInfo(TV_HARDWARE_DOCS);

    private static final Set<String> UNSUPPORTED_TV_FEATURES = ImmutableSet.of(
            "android.hardware.telephony",
            "android.hardware.touchscreen",
            "android.hardware.faketouch",
            "android.hardware.camera",
            "android.hardware.camera.autofocus",
            "android.hardware.camera.flash",
            "android.hardware.nfc",
            "android.hardware.location.gps",
            "android.hardware.microphone",
            "android.hardware.screen.portrait"
    );

    @Override
    public Collection<String> getApplicableElements() {
        return ImmutableSet.of(TAG_USES_FEATURE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        if (!UNSUPPORTED_TV_FEATURES.contains(name)) {
            return;
        }

        String required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
        if ("false".equals(required)) {
            return;
        }

        context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Unsupported TV hardware feature `" + name + "` should be marked required=\"false\"");
    }
}
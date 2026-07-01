package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "UnsupportedTvHardware",
                    "Unsupported TV Hardware Feature",
                    "The `<uses-feature>` element should not require this unsupported TV "
                            + "hardware feature. Any `uses-feature` not explicitly marked with "
                            + "`required=\"false\"` is necessary on the device to be installed "
                            + "on. Ensure that any features that might prevent it from being "
                            + "installed on a TV device are reviewed and marked as not required "
                            + "in the manifest.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private static final String TAG_USES_FEATURE = "uses-feature";

    private static final Set<String> UNSUPPORTED_TV_FEATURES =
            new HashSet<>(
                    Arrays.asList(
                            "android.hardware.autofocus",
                            "android.hardware.camera",
                            "android.hardware.consumerir",
                            "android.hardware.location",
                            "android.hardware.location.gps",
                            "android.hardware.nfc",
                            "android.hardware.screen.portrait",
                            "android.hardware.sensor",
                            "android.hardware.telephony",
                            "android.hardware.touchscreen",
                            "android.hardware.type.automotive",
                            "android.hardware.type.watch",
                            "android.hardware.usb",
                            "android.hardware.wifi"));

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(ANDROID_URI, "name");
        if (name == null || name.isEmpty()) {
            return;
        }

        if (isUnsupportedTvFeature(name)) {
            String required = element.getAttributeNS(ANDROID_URI, "required");
            if (!"false".equals(required)) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        String.format(
                                "The `%1$s` feature is not supported on Android TV devices; any "
                                        + "`uses-feature` not explicitly marked with "
                                        + "`required=\"false\"` is necessary on the device to be "
                                        + "installed on.",
                                name));
            }
        }
    }

    private static boolean isUnsupportedTvFeature(String name) {
        if (UNSUPPORTED_TV_FEATURES.contains(name)) {
            return true;
        }
        if (name.startsWith("android.hardware.sensor.")) {
            return true;
        }
        if (name.startsWith("android.hardware.touchscreen.")) {
            return true;
        }
        if (name.startsWith("android.hardware.camera.")) {
            return true;
        }
        if (name.startsWith("android.hardware.telephony.")) {
            return true;
        }
        if (name.startsWith("android.hardware.type.")) {
            return true;
        }
        if (name.startsWith("android.hardware.usb.")) {
            return true;
        }
        return false;
    }
}
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
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String ATTR_NAME = "android:name";
    private static final String ATTR_REQUIRED = "android:required";
    private static final String MANIFEST_FILE = "AndroidManifest.xml";

    /**
     * Hardware features that are not supported on TV devices.
     * See https://developer.android.com/training/tv/start/hardware.html#unsupported-features
     */
    private static final Set<String> UNSUPPORTED_TV_HARDWARE_FEATURES =
            new HashSet<>(
                    Arrays.asList(
                            "android.hardware.bluetooth",
                            "android.hardware.camera",
                            "android.hardware.camera.autofocus",
                            "android.hardware.camera.capability.manual_post_processing",
                            "android.hardware.camera.capability.manual_sensor",
                            "android.hardware.camera.capability.raw",
                            "android.hardware.camera.flash",
                            "android.hardware.camera.front",
                            "android.hardware.camera.level.full",
                            "android.hardware.location.gps",
                            "android.hardware.microphone",
                            "android.hardware.nfc",
                            "android.hardware.nfc.hce",
                            "android.hardware.sensor.accelerometer",
                            "android.hardware.sensor.barometer",
                            "android.hardware.sensor.compass",
                            "android.hardware.sensor.gyroscope",
                            "android.hardware.sensor.light",
                            "android.hardware.sensor.proximity",
                            "android.hardware.sensor.stepcounter",
                            "android.hardware.sensor.stepdetector",
                            "android.hardware.telephony",
                            "android.hardware.telephony.cdma",
                            "android.hardware.telephony.gsm",
                            "android.hardware.touchscreen",
                            "android.hardware.touchscreen.multitouch",
                            "android.hardware.touchscreen.multitouch.distinct",
                            "android.hardware.touchscreen.multitouch.jazzhand",
                            "android.hardware.usb.accessory",
                            "android.hardware.usb.host",
                            "android.hardware.wifi"));

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue UNSUPPORTED_TV_HARDWARE =
            Issue.create(
                    "UnsupportedTvHardware",
                    "Unsupported TV Hardware Feature",
                    "The `<uses-feature>` element should not require this unsupported TV "
                            + "hardware feature. Any uses-feature not explicitly marked with "
                            + "`required=\"false\"` is necessary on the device to be installed "
                            + "on. Ensure that any features that might prevent it from being "
                            + "installed on a TV device are reviewed and marked as not "
                            + "required in the manifest.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    // Keep backward-compatible reference
    public static final Issue ISSUE = UNSUPPORTED_TV_HARDWARE;

    private boolean mIsManifestFile;

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIsManifestFile = MANIFEST_FILE.equals(context.file.getName());
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        mIsManifestFile = false;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!mIsManifestFile) {
            return;
        }

        Attr nameAttr = element.getAttributeNode(ATTR_NAME);
        if (nameAttr == null) {
            return;
        }

        String featureName = nameAttr.getValue();
        if (!UNSUPPORTED_TV_HARDWARE_FEATURES.contains(featureName)) {
            return;
        }

        // Check if required="false" is set
        Attr requiredAttr = element.getAttributeNode(ATTR_REQUIRED);
        if (requiredAttr != null) {
            String requiredValue = requiredAttr.getValue();
            if ("false".equalsIgnoreCase(requiredValue)) {
                // Feature is marked as not required, so it's fine
                return;
            }
        }

        // The feature is required (either explicitly or by default) and is unsupported on TV
        String message =
                String.format(
                        "Feature `%s` is not supported on TV. Consider marking it as"
                                + " `required=\"false\"` in the manifest.",
                        featureName);

        context.report(UNSUPPORTED_TV_HARDWARE, element, context.getLocation(nameAttr), message);
    }
}
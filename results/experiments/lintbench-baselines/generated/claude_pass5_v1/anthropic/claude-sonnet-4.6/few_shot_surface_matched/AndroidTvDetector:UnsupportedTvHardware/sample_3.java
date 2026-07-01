package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_REQUIRED;
import static com.android.SdkConstants.VALUE_TRUE;
import static com.android.xml.AndroidManifest.NODE_USES_FEATURE;

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
import java.util.HashSet;
import java.util.Set;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue UNSUPPORTED_TV_HARDWARE =
            Issue.create(
                    "UnsupportedTvHardware",
                    "Unsupported TV Hardware Feature",
                    "The `<uses-feature>` element should not require this unsupported TV hardware"
                            + " feature. Any uses-feature not explicitly marked with"
                            + " `required=\"false\"` is necessary on the device to be installed"
                            + " on. Ensure that any features that might prevent it from being"
                            + " installed on a TV device are reviewed and marked as not required"
                            + " in the manifest.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    /** Hardware features that are not supported on Android TV */
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
                            "android.hardware.location",
                            "android.hardware.location.gps",
                            "android.hardware.location.network",
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

    /** Whether the manifest targets TV (has a uses-feature for leanback) */
    private boolean mHasLeanbackFeature;

    /** Pending unsupported hardware feature elements found before we confirm TV targeting */
    private final Set<Element> mUnsupportedHardwareElements = new HashSet<>();

    /** XmlContext saved for afterCheckFile reporting */
    private XmlContext mContext;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(NODE_USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(@NonNull com.android.tools.lint.detector.api.Context context) {
        mHasLeanbackFeature = false;
        mUnsupportedHardwareElements.clear();
        mContext = null;
    }

    @Override
    public void afterCheckFile(@NonNull com.android.tools.lint.detector.api.Context context) {
        if (mHasLeanbackFeature && mContext != null) {
            for (Element element : mUnsupportedHardwareElements) {
                String featureName = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
                mContext.report(
                        UNSUPPORTED_TV_HARDWARE,
                        element,
                        mContext.getLocation(element),
                        String.format(
                                "Requiring feature `%1$s` is unsupported on TV and may prevent"
                                        + " it from being installed on a TV device. Consider"
                                        + " marking it as `required=\"false\"`.",
                                featureName));
            }
        }
        mUnsupportedHardwareElements.clear();
        mContext = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        mContext = context;

        String featureName = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (featureName == null || featureName.isEmpty()) {
            return;
        }

        // Check if this is the leanback feature declaration
        if ("android.software.leanback".equals(featureName)) {
            mHasLeanbackFeature = true;
            return;
        }

        // Check if this is an unsupported TV hardware feature
        if (UNSUPPORTED_TV_HARDWARE_FEATURES.contains(featureName)) {
            // Check if required is explicitly set to false
            String requiredValue = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
            boolean required =
                    requiredValue == null
                            || requiredValue.isEmpty()
                            || VALUE_TRUE.equals(requiredValue);
            if (required) {
                mUnsupportedHardwareElements.add(element);
            }
        }
    }
}
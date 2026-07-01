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
    private static final String TAG_CATEGORY = "category";
    private static final String ATTR_ANDROID_NAME = "android:name";
    private static final String CATEGORY_LEANBACK_LAUNCHER = "android.intent.category.LEANBACK_LAUNCHER";

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
                            "android.hardware.usb.host"));

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

    /** Whether the manifest targets TV (has LEANBACK_LAUNCHER category) */
    private boolean mIsTargetingTv;

    public AndroidTvDetector() {}

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIsTargetingTv = false;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        // Nothing needed here; we check during visitElement
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check if this manifest is targeting TV by looking for LEANBACK_LAUNCHER category
        // We need to check the whole document for the LEANBACK_LAUNCHER category
        if (!mIsTargetingTv) {
            mIsTargetingTv = isManifestTargetingTv(element);
        }

        if (!mIsTargetingTv) {
            return;
        }

        String featureName = element.getAttributeNS(
                "http://schemas.android.com/apk/res/android", "name");
        if (featureName == null || featureName.isEmpty()) {
            // Try without namespace
            featureName = element.getAttribute("android:name");
        }

        if (featureName == null || featureName.isEmpty()) {
            return;
        }

        if (!UNSUPPORTED_TV_HARDWARE_FEATURES.contains(featureName)) {
            return;
        }

        // Check if required="false"
        String requiredValue = element.getAttributeNS(
                "http://schemas.android.com/apk/res/android", "required");
        if (requiredValue == null || requiredValue.isEmpty()) {
            requiredValue = element.getAttribute("android:required");
        }

        // If required is explicitly set to false, it's fine
        if ("false".equals(requiredValue)) {
            return;
        }

        // Report the issue
        Attr nameAttr = element.getAttributeNodeNS(
                "http://schemas.android.com/apk/res/android", "name");
        if (nameAttr == null) {
            context.report(
                    UNSUPPORTED_TV_HARDWARE,
                    element,
                    context.getLocation(element),
                    "Unsupported TV hardware feature `"
                            + featureName
                            + "`");
        } else {
            context.report(
                    UNSUPPORTED_TV_HARDWARE,
                    element,
                    context.getLocation(nameAttr),
                    "Unsupported TV hardware feature `"
                            + featureName
                            + "`");
        }
    }

    /**
     * Checks whether the manifest is targeting TV by searching for the LEANBACK_LAUNCHER
     * category anywhere in the document.
     */
    private boolean isManifestTargetingTv(@NonNull Element element) {
        org.w3c.dom.Document document = element.getOwnerDocument();
        if (document == null) {
            return false;
        }
        org.w3c.dom.NodeList categories = document.getElementsByTagName("category");
        for (int i = 0; i < categories.getLength(); i++) {
            org.w3c.dom.Node node = categories.item(i);
            if (node instanceof Element) {
                Element categoryElement = (Element) node;
                String name = categoryElement.getAttributeNS(
                        "http://schemas.android.com/apk/res/android", "name");
                if (name == null || name.isEmpty()) {
                    name = categoryElement.getAttribute("android:name");
                }
                if (CATEGORY_LEANBACK_LAUNCHER.equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }
}
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
    private static final String ATTR_PACKAGE = "package";

    private static final String ANDROID_TV_LAUNCHER_CATEGORY =
            "android.intent.category.LEANBACK_LAUNCHER";

    /**
     * Hardware features that are not supported on Android TV devices.
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
                            "android.hardware.screen.landscape",
                            "android.hardware.screen.portrait",
                            "android.hardware.telephony",
                            "android.hardware.telephony.cdma",
                            "android.hardware.telephony.gsm",
                            "android.hardware.touchscreen",
                            "android.hardware.touchscreen.multitouch",
                            "android.hardware.touchscreen.multitouch.distinct",
                            "android.hardware.touchscreen.multitouch.jazzhand",
                            "android.hardware.usb.accessory",
                            "android.hardware.usb.host",
                            "android.hardware.wifi",
                            "android.hardware.wifi.direct"));

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

    /** Whether the manifest targets Android TV (has the LEANBACK_LAUNCHER category). */
    private boolean mIsTargetingTv;

    public AndroidTvDetector() {}

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIsTargetingTv = false;
        // We need to check if this manifest targets TV. We'll do a preliminary scan
        // by checking if the manifest file contains the LEANBACK_LAUNCHER category.
        // This will be determined during visitElement as we scan the document.
        // We'll use a two-pass approach: collect all data during visitElement,
        // then report in afterCheckFile.
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        // Reset state after processing each file
        mIsTargetingTv = false;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Check if the manifest is targeting TV by looking for LEANBACK_LAUNCHER
        // We check the full document for the leanback launcher category
        if (!mIsTargetingTv) {
            mIsTargetingTv = isManifestTargetingTv(context);
        }

        if (!mIsTargetingTv) {
            return;
        }

        // Get the feature name
        String featureName = element.getAttribute(ATTR_NAME);
        if (featureName == null || featureName.isEmpty()) {
            // Try without namespace prefix
            featureName = element.getAttributeNS(
                    "http://schemas.android.com/apk/res/android", "name");
        }

        if (featureName == null || featureName.isEmpty()) {
            return;
        }

        // Check if this is an unsupported TV hardware feature
        if (!UNSUPPORTED_TV_HARDWARE_FEATURES.contains(featureName)) {
            return;
        }

        // Check if required is explicitly set to false
        String required = element.getAttribute(ATTR_REQUIRED);
        if (required == null || required.isEmpty()) {
            required = element.getAttributeNS(
                    "http://schemas.android.com/apk/res/android", "required");
        }

        // If required is explicitly "false", it's fine
        if ("false".equals(required)) {
            return;
        }

        // Report the issue
        Attr nameAttr = element.getAttributeNode(ATTR_NAME);
        if (nameAttr == null) {
            nameAttr = element.getAttributeNodeNS(
                    "http://schemas.android.com/apk/res/android", "name");
        }

        String message =
                String.format(
                        "Unsupported TV hardware feature `%s`", featureName);

        if (nameAttr != null) {
            context.report(UNSUPPORTED_TV_HARDWARE, element, context.getLocation(nameAttr),
                    message);
        } else {
            context.report(UNSUPPORTED_TV_HARDWARE, element, context.getLocation(element),
                    message);
        }
    }

    /**
     * Checks whether the manifest targets Android TV by looking for the
     * LEANBACK_LAUNCHER category in any intent-filter elements.
     */
    private boolean isManifestTargetingTv(@NonNull XmlContext context) {
        org.w3c.dom.Document document = context.document;
        if (document == null) {
            return false;
        }

        // Look for any category element with LEANBACK_LAUNCHER
        org.w3c.dom.NodeList categoryNodes =
                document.getElementsByTagName("category");
        for (int i = 0; i < categoryNodes.getLength(); i++) {
            org.w3c.dom.Node node = categoryNodes.item(i);
            if (node instanceof Element) {
                Element categoryElement = (Element) node;
                String name = categoryElement.getAttribute("android:name");
                if (name == null || name.isEmpty()) {
                    name = categoryElement.getAttributeNS(
                            "http://schemas.android.com/apk/res/android", "name");
                }
                if (ANDROID_TV_LAUNCHER_CATEGORY.equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }
}
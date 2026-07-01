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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String TAG_USES_PERMISSION = "uses-permission";
    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_REQUIRED = "required";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    /**
     * Map from permission name to the implied hardware feature it requires.
     * Based on https://developer.android.com/guide/topics/manifest/uses-feature-element.html#permissions
     */
    private static final Map<String, String> PERMISSION_TO_FEATURE = new HashMap<>();

    static {
        PERMISSION_TO_FEATURE.put(
                "android.permission.BLUETOOTH",
                "android.hardware.bluetooth");
        PERMISSION_TO_FEATURE.put(
                "android.permission.BLUETOOTH_ADMIN",
                "android.hardware.bluetooth");
        PERMISSION_TO_FEATURE.put(
                "android.permission.CAMERA",
                "android.hardware.camera");
        PERMISSION_TO_FEATURE.put(
                "android.permission.CHANGE_WIFI_MULTICAST_STATE",
                "android.hardware.wifi");
        PERMISSION_TO_FEATURE.put(
                "android.permission.CHANGE_WIFI_STATE",
                "android.hardware.wifi");
        PERMISSION_TO_FEATURE.put(
                "android.permission.ACCESS_WIFI_STATE",
                "android.hardware.wifi");
        PERMISSION_TO_FEATURE.put(
                "android.permission.NFC",
                "android.hardware.nfc");
        PERMISSION_TO_FEATURE.put(
                "android.permission.RECORD_AUDIO",
                "android.hardware.microphone");
        PERMISSION_TO_FEATURE.put(
                "android.permission.USE_SIP",
                "android.hardware.sip.voip");
        PERMISSION_TO_FEATURE.put(
                "android.permission.ACCESS_FINE_LOCATION",
                "android.hardware.location.gps");
        PERMISSION_TO_FEATURE.put(
                "android.permission.ACCESS_COARSE_LOCATION",
                "android.hardware.location.network");
        PERMISSION_TO_FEATURE.put(
                "android.permission.ACCESS_MOCK_LOCATION",
                "android.hardware.location");
        PERMISSION_TO_FEATURE.put(
                "android.permission.ACCESS_LOCATION_EXTRA_COMMANDS",
                "android.hardware.location");
        PERMISSION_TO_FEATURE.put(
                "android.permission.INSTALL_LOCATION_PROVIDER",
                "android.hardware.location");
        PERMISSION_TO_FEATURE.put(
                "android.permission.CALL_PHONE",
                "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put(
                "android.permission.CALL_PRIVILEGED",
                "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put(
                "android.permission.MODIFY_PHONE_STATE",
                "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put(
                "android.permission.PROCESS_OUTGOING_CALLS",
                "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put(
                "android.permission.READ_SMS",
                "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put(
                "android.permission.RECEIVE_SMS",
                "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put(
                "android.permission.RECEIVE_MMS",
                "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put(
                "android.permission.RECEIVE_WAP_PUSH",
                "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put(
                "android.permission.SEND_SMS",
                "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put(
                "android.permission.WRITE_APN_SETTINGS",
                "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put(
                "android.permission.WRITE_SMS",
                "android.hardware.telephony");
    }

    /**
     * Set of hardware features that are not supported on Android TV.
     */
    private static final Set<String> UNSUPPORTED_TV_HARDWARE_FEATURES = new HashSet<>(
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
                    "android.hardware.sip.voip",
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
                    "android.hardware.wifi.direct",
                    "android.hardware.location"
            ));

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE =
            Issue.create(
                    "PermissionImpliesUnsupportedHardware",
                    "Permission Implies Unsupported Hardware",
                    "The `<uses-permission>` element should not require a permission that implies "
                            + "an unsupported TV hardware feature. Google Play assumes that certain "
                            + "hardware related permissions indicate that the underlying hardware "
                            + "features are required by default. To fix the issue, consider declaring "
                            + "the corresponding `uses-feature` element with `required=\"false\"` attribute.",
                    Category.CORRECTNESS,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION)
                    .addMoreInfo(
                            "https://developer.android.com/guide/topics/manifest/uses-feature-element.html#permissions");

    // Keep for compatibility with the skeleton's ISSUE field name
    public static final Issue ISSUE = PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE;

    /** Set of features that are explicitly declared with required="false" in the manifest */
    private Set<String> mExplicitlyNotRequiredFeatures;

    /** Whether this manifest targets Android TV (has leanback feature or category) */
    private boolean mIsTargetingTv;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_USES_PERMISSION, TAG_USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mExplicitlyNotRequiredFeatures = new HashSet<>();
        mIsTargetingTv = false;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        // Nothing to do here; we report issues during visitElement
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();

        if (TAG_USES_FEATURE.equals(tagName)) {
            handleUsesFeature(element);
        } else if (TAG_USES_PERMISSION.equals(tagName)) {
            handleUsesPermission(context, element);
        }
    }

    private void handleUsesFeature(@NonNull Element element) {
        Attr nameAttr = element.getAttributeNodeNS(ANDROID_NS, ATTR_NAME);
        if (nameAttr == null) {
            return;
        }
        String featureName = nameAttr.getValue();
        if (featureName == null || featureName.isEmpty()) {
            return;
        }

        // Check if this feature is explicitly marked as not required
        Attr requiredAttr = element.getAttributeNodeNS(ANDROID_NS, ATTR_REQUIRED);
        if (requiredAttr != null) {
            String requiredValue = requiredAttr.getValue();
            if ("false".equals(requiredValue)) {
                mExplicitlyNotRequiredFeatures.add(featureName);
            }
        }
    }

    private void handleUsesPermission(@NonNull XmlContext context, @NonNull Element element) {
        Attr nameAttr = element.getAttributeNodeNS(ANDROID_NS, ATTR_NAME);
        if (nameAttr == null) {
            return;
        }
        String permissionName = nameAttr.getValue();
        if (permissionName == null || permissionName.isEmpty()) {
            return;
        }

        // Check if this permission implies a hardware feature
        String impliedFeature = PERMISSION_TO_FEATURE.get(permissionName);
        if (impliedFeature == null) {
            return;
        }

        // Check if the implied feature is one that's not supported on TV
        if (!UNSUPPORTED_TV_HARDWARE_FEATURES.contains(impliedFeature)) {
            return;
        }

        // Check if the feature has been explicitly declared as not required
        if (mExplicitlyNotRequiredFeatures.contains(impliedFeature)) {
            return;
        }

        // Also check the document for uses-feature declarations that we may not have
        // visited yet (since XML parsing is sequential, we do a second pass check)
        if (isFeatureDeclaredNotRequired(element, impliedFeature)) {
            return;
        }

        // Report the issue
        String message = String.format(
                "Permission `%1$s` implies that the app uses the feature `%2$s`. "
                        + "If the app does not require this feature, it is recommended to "
                        + "declare `<uses-feature android:name=\"%2$s\" android:required=\"false\" />`.",
                permissionName, impliedFeature);

        context.report(
                PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE,
                element,
                context.getLocation(element),
                message);
    }

    /**
     * Checks the manifest document to see if the given feature is declared with required="false".
     * This handles cases where the uses-feature element appears after the uses-permission element.
     */
    private boolean isFeatureDeclaredNotRequired(@NonNull Element element, @NonNull String featureName) {
        // Walk up to the root manifest element
        org.w3c.dom.Node parent = element.getParentNode();
        if (!(parent instanceof Element)) {
            return false;
        }
        Element manifest = (Element) parent;

        // Look through all uses-feature elements in the manifest
        NodeList features = manifest.getElementsByTagName(TAG_USES_FEATURE);
        for (int i = 0; i < features.getLength(); i++) {
            org.w3c.dom.Node node = features.item(i);
            if (!(node instanceof Element)) {
                continue;
            }
            Element featureElement = (Element) node;
            Attr nameAttr = featureElement.getAttributeNodeNS(ANDROID_NS, ATTR_NAME);
            if (nameAttr == null) {
                continue;
            }
            String name = nameAttr.getValue();
            if (!featureName.equals(name)) {
                continue;
            }
            Attr requiredAttr = featureElement.getAttributeNodeNS(ANDROID_NS, ATTR_REQUIRED);
            if (requiredAttr != null && "false".equals(requiredAttr.getValue())) {
                return true;
            }
        }
        return false;
    }
}
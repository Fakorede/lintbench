package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Checks for uses-permission elements that imply unsupported TV hardware features.
 */
public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final Implementation IMPLEMENTATION = new Implementation(
            AndroidTvDetector.class,
            Scope.MANIFEST_SCOPE);

    /** Permission implies unsupported hardware */
    public static final Issue PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE = Issue.create(
            "PermissionImpliesUnsupportedHardware",
            "Permission Implies Unsupported Hardware",
            "The `<uses-permission>` element should not require a permission that implies " +
            "an unsupported TV hardware feature. Google Play assumes that certain hardware " +
            "related permissions indicate that the underlying hardware features are required " +
            "by default. To fix the issue, consider declaring the corresponding `uses-feature` " +
            "element with `required=\"false\"` attribute.",
            Category.CORRECTNESS,
            3,
            Severity.WARNING,
            IMPLEMENTATION)
            .addMoreInfo(
                    "https://developer.android.com/guide/topics/manifest/uses-feature-element.html#permissions");

    private static final String ANDROID_MANIFEST_TAG = "manifest";
    private static final String TAG_USES_PERMISSION = "uses-permission";
    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_REQUIRED = "required";
    private static final String VALUE_FALSE = "false";

    /**
     * Map from permission name to the hardware feature it implies.
     * Based on https://developer.android.com/guide/topics/manifest/uses-feature-element.html#permissions
     */
    private static final Map<String, String> PERMISSION_TO_FEATURE;

    static {
        PERMISSION_TO_FEATURE = new HashMap<>();
        PERMISSION_TO_FEATURE.put("android.permission.BLUETOOTH",
                "android.hardware.bluetooth");
        PERMISSION_TO_FEATURE.put("android.permission.BLUETOOTH_ADMIN",
                "android.hardware.bluetooth");
        PERMISSION_TO_FEATURE.put("android.permission.CAMERA",
                "android.hardware.camera");
        PERMISSION_TO_FEATURE.put("android.permission.CHANGE_WIFI_MULTICAST_STATE",
                "android.hardware.wifi");
        PERMISSION_TO_FEATURE.put("android.permission.CHANGE_WIFI_STATE",
                "android.hardware.wifi");
        PERMISSION_TO_FEATURE.put("android.permission.ACCESS_WIFI_STATE",
                "android.hardware.wifi");
        PERMISSION_TO_FEATURE.put("android.permission.NFC",
                "android.hardware.nfc");
        PERMISSION_TO_FEATURE.put("android.permission.RECORD_AUDIO",
                "android.hardware.microphone");
        PERMISSION_TO_FEATURE.put("android.permission.USE_SIP",
                "android.hardware.sip.voip");
        PERMISSION_TO_FEATURE.put("android.permission.ACCESS_COARSE_LOCATION",
                "android.hardware.location.network");
        PERMISSION_TO_FEATURE.put("android.permission.ACCESS_FINE_LOCATION",
                "android.hardware.location.gps");
        PERMISSION_TO_FEATURE.put("android.permission.ACCESS_MOCK_LOCATION",
                "android.hardware.location");
        PERMISSION_TO_FEATURE.put("android.permission.ACCESS_LOCATION_EXTRA_COMMANDS",
                "android.hardware.location");
        PERMISSION_TO_FEATURE.put("android.permission.INSTALL_LOCATION_PROVIDER",
                "android.hardware.location");
        PERMISSION_TO_FEATURE.put("android.permission.CALL_PHONE",
                "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.CALL_PRIVILEGED",
                "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.MODIFY_PHONE_STATE",
                "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.PROCESS_OUTGOING_CALLS",
                "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.READ_SMS",
                "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.RECEIVE_SMS",
                "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.RECEIVE_MMS",
                "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.RECEIVE_WAP_PUSH",
                "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.SEND_SMS",
                "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.WRITE_APN_SETTINGS",
                "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.WRITE_SMS",
                "android.hardware.telephony");
    }

    /**
     * Hardware features that are not supported on Android TV.
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
                    "android.hardware.wifi"
            ));

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_USES_PERMISSION, TAG_USES_FEATURE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // We process at the document level to have full context of all elements.
        // Individual element visits are used to trigger document-level analysis.
        // We'll do the analysis once when we see the first uses-permission or uses-feature.
        // Actually, we'll override visitDocument instead.
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        // Collect all uses-feature elements that have required="false"
        // Map from feature name -> whether it's explicitly declared as not required
        Map<String, Boolean> declaredFeatures = new HashMap<>();

        NodeList featureNodes = root.getElementsByTagName(TAG_USES_FEATURE);
        for (int i = 0; i < featureNodes.getLength(); i++) {
            Element featureElement = (Element) featureNodes.item(i);
            String featureName = featureElement.getAttributeNS(
                    "http://schemas.android.com/apk/res/android", ATTR_NAME);
            if (featureName == null || featureName.isEmpty()) {
                featureName = featureElement.getAttribute(ATTR_NAME);
            }
            if (featureName == null || featureName.isEmpty()) {
                continue;
            }

            String requiredValue = featureElement.getAttributeNS(
                    "http://schemas.android.com/apk/res/android", ATTR_REQUIRED);
            if (requiredValue == null || requiredValue.isEmpty()) {
                requiredValue = featureElement.getAttribute(ATTR_REQUIRED);
            }

            boolean required = !VALUE_FALSE.equalsIgnoreCase(requiredValue);
            // Only store as "not required" if explicitly set to false
            if (!required) {
                declaredFeatures.put(featureName, Boolean.FALSE);
            } else {
                // Feature is declared as required (or default required)
                if (!declaredFeatures.containsKey(featureName)) {
                    declaredFeatures.put(featureName, Boolean.TRUE);
                }
            }
        }

        // Now check all uses-permission elements
        NodeList permissionNodes = root.getElementsByTagName(TAG_USES_PERMISSION);
        for (int i = 0; i < permissionNodes.getLength(); i++) {
            Element permissionElement = (Element) permissionNodes.item(i);
            String permissionName = permissionElement.getAttributeNS(
                    "http://schemas.android.com/apk/res/android", ATTR_NAME);
            if (permissionName == null || permissionName.isEmpty()) {
                permissionName = permissionElement.getAttribute(ATTR_NAME);
            }
            if (permissionName == null || permissionName.isEmpty()) {
                continue;
            }

            String impliedFeature = PERMISSION_TO_FEATURE.get(permissionName);
            if (impliedFeature == null) {
                continue;
            }

            // Check if this implied feature is unsupported on TV
            if (!UNSUPPORTED_TV_HARDWARE_FEATURES.contains(impliedFeature)) {
                continue;
            }

            // Check if there's a uses-feature declaration with required="false"
            Boolean featureRequired = declaredFeatures.get(impliedFeature);
            if (featureRequired != null && !featureRequired) {
                // Feature is declared with required="false", so it's fine
                continue;
            }

            // Report the issue
            String message = String.format(
                    "Permission exists without corresponding `<uses-feature android:name=\"%1$s\" " +
                    "android:required=\"false\">` tag",
                    impliedFeature);

            context.report(
                    PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE,
                    permissionElement,
                    context.getLocation(permissionElement),
                    message);
        }
    }
}
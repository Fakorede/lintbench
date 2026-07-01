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
 * Detector for checking that permissions that imply hardware features are properly
 * declared with required="false" for Android TV compatibility.
 */
public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE = Issue.create(
            "PermissionImpliesUnsupportedHardware",
            "Permission Implies Unsupported Hardware",
            "The `<uses-permission>` element should not require a permission that implies " +
            "an unsupported TV hardware feature. Google Play assumes that certain hardware " +
            "related permissions indicate that the underlying hardware features are required " +
            "by default. To fix the issue, consider declaring the corresponding `uses-feature` " +
            "element with `required=\"false\"` attribute.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(
                    AndroidTvDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    ).addMoreInfo(
            "https://developer.android.com/guide/topics/manifest/uses-feature-element.html#permissions"
    );

    // Mapping from permission to the hardware feature it implies
    private static final Map<String, String> PERMISSION_TO_FEATURE = new HashMap<>();

    // Hardware features that are not supported on TV
    private static final Set<String> UNSUPPORTED_TV_HARDWARE_FEATURES = new HashSet<>();

    static {
        PERMISSION_TO_FEATURE.put("android.permission.BLUETOOTH",
                "android.hardware.bluetooth");
        PERMISSION_TO_FEATURE.put("android.permission.BLUETOOTH_ADMIN",
                "android.hardware.bluetooth");
        PERMISSION_TO_FEATURE.put("android.permission.CAMERA",
                "android.hardware.camera");
        PERMISSION_TO_FEATURE.put("android.permission.RECORD_AUDIO",
                "android.hardware.microphone");
        PERMISSION_TO_FEATURE.put("android.permission.ACCESS_WIFI_STATE",
                "android.hardware.wifi");
        PERMISSION_TO_FEATURE.put("android.permission.CHANGE_WIFI_STATE",
                "android.hardware.wifi");
        PERMISSION_TO_FEATURE.put("android.permission.CHANGE_WIFI_MULTICAST_STATE",
                "android.hardware.wifi");
        PERMISSION_TO_FEATURE.put("android.permission.ACCESS_FINE_LOCATION",
                "android.hardware.location.gps");
        PERMISSION_TO_FEATURE.put("android.permission.ACCESS_COARSE_LOCATION",
                "android.hardware.location.network");
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
        PERMISSION_TO_FEATURE.put("android.permission.NFC",
                "android.hardware.nfc");
        PERMISSION_TO_FEATURE.put("android.permission.FLASHLIGHT",
                "android.hardware.camera.flash");

        UNSUPPORTED_TV_HARDWARE_FEATURES.add("android.hardware.bluetooth");
        UNSUPPORTED_TV_HARDWARE_FEATURES.add("android.hardware.camera");
        UNSUPPORTED_TV_HARDWARE_FEATURES.add("android.hardware.camera.autofocus");
        UNSUPPORTED_TV_HARDWARE_FEATURES.add("android.hardware.camera.flash");
        UNSUPPORTED_TV_HARDWARE_FEATURES.add("android.hardware.location");
        UNSUPPORTED_TV_HARDWARE_FEATURES.add("android.hardware.location.gps");
        UNSUPPORTED_TV_HARDWARE_FEATURES.add("android.hardware.location.network");
        UNSUPPORTED_TV_HARDWARE_FEATURES.add("android.hardware.microphone");
        UNSUPPORTED_TV_HARDWARE_FEATURES.add("android.hardware.nfc");
        UNSUPPORTED_TV_HARDWARE_FEATURES.add("android.hardware.telephony");
        UNSUPPORTED_TV_HARDWARE_FEATURES.add("android.hardware.touchscreen");
        UNSUPPORTED_TV_HARDWARE_FEATURES.add("android.hardware.wifi");
    }

    private static final String ANDROID_TV_CATEGORY = "android.intent.category.LEANBACK_LAUNCHER";
    private static final String USES_FEATURE = "uses-feature";
    private static final String USES_PERMISSION = "uses-permission";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_REQUIRED = "required";
    private static final String ANDROID_NS = SdkConstants.ANDROID_URI;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(USES_PERMISSION);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        // Only check manifests that target Android TV (have LEANBACK_LAUNCHER category)
        if (!isTvApp(element)) {
            return;
        }

        String permissionName = element.getAttributeNS(ANDROID_NS, ATTR_NAME);
        if (permissionName == null || permissionName.isEmpty()) {
            return;
        }

        String impliedFeature = PERMISSION_TO_FEATURE.get(permissionName);
        if (impliedFeature == null) {
            return;
        }

        if (!UNSUPPORTED_TV_HARDWARE_FEATURES.contains(impliedFeature)) {
            return;
        }

        // Check if there's a corresponding uses-feature element with required="false"
        if (!hasFeatureDeclaredAsNotRequired(element, impliedFeature)) {
            Attr nameAttr = element.getAttributeNodeNS(ANDROID_NS, ATTR_NAME);
            String message = String.format(
                    "Permission `%1$s` implies hardware feature `%2$s` which is not supported by " +
                    "all Android TV devices. Consider adding `<uses-feature " +
                    "android:name=\"%2$s\" android:required=\"false\"/>` to your manifest.",
                    permissionName, impliedFeature);

            if (nameAttr != null) {
                context.report(PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE, element,
                        context.getLocation(nameAttr), message);
            } else {
                context.report(PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE, element,
                        context.getLocation(element), message);
            }
        }
    }

    /**
     * Checks whether the manifest declares the app as an Android TV app by looking
     * for the LEANBACK_LAUNCHER category in an intent-filter.
     */
    private boolean isTvApp(Element element) {
        Document document = element.getOwnerDocument();
        if (document == null) {
            return false;
        }

        // Search for LEANBACK_LAUNCHER category in the manifest
        NodeList categoryNodes = document.getElementsByTagName("category");
        for (int i = 0; i < categoryNodes.getLength(); i++) {
            Element categoryElement = (Element) categoryNodes.item(i);
            String categoryName = categoryElement.getAttributeNS(ANDROID_NS, ATTR_NAME);
            if (ANDROID_TV_CATEGORY.equals(categoryName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether the manifest has a uses-feature element for the given feature
     * with required="false".
     */
    private boolean hasFeatureDeclaredAsNotRequired(Element element, String featureName) {
        Document document = element.getOwnerDocument();
        if (document == null) {
            return false;
        }

        NodeList featureNodes = document.getElementsByTagName(USES_FEATURE);
        for (int i = 0; i < featureNodes.getLength(); i++) {
            Element featureElement = (Element) featureNodes.item(i);
            String name = featureElement.getAttributeNS(ANDROID_NS, ATTR_NAME);
            if (featureName.equals(name)) {
                String required = featureElement.getAttributeNS(ANDROID_NS, ATTR_REQUIRED);
                // If required is explicitly set to "false", we're good
                if ("false".equals(required)) {
                    return true;
                }
            }
        }
        return false;
    }
}
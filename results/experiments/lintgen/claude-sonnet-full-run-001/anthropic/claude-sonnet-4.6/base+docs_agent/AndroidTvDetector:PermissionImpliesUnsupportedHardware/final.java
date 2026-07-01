package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.*;

import static com.android.SdkConstants.*;
import static com.android.xml.AndroidManifest.*;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE = Issue.create(
            "PermissionImpliesUnsupportedHardware",
            "Permission Implies Unsupported Hardware",
            "The `<uses-permission>` element should not require a permission that implies an " +
            "unsupported TV hardware feature. Google Play assumes that certain hardware related " +
            "permissions indicate that the underlying hardware features are required by default. " +
            "To fix the issue, consider declaring the corresponding `uses-feature` element with " +
            "`required=\"false\"` attribute.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE))
            .addMoreInfo("https://developer.android.com/guide/topics/manifest/uses-feature-element.html#permissions");

    /**
     * Map from permission name to the hardware feature it implies.
     * Based on https://developer.android.com/guide/topics/manifest/uses-feature-element.html#permissions
     */
    private static final Map<String, String> PERMISSION_TO_FEATURE;

    static {
        Map<String, String> map = new HashMap<>();
        map.put("android.permission.BLUETOOTH",              "android.hardware.bluetooth");
        map.put("android.permission.BLUETOOTH_ADMIN",        "android.hardware.bluetooth");
        map.put("android.permission.CAMERA",                 "android.hardware.camera");
        map.put("android.permission.CHANGE_WIFI_MULTICAST_STATE", "android.hardware.wifi");
        map.put("android.permission.CHANGE_WIFI_STATE",      "android.hardware.wifi");
        map.put("android.permission.ACCESS_WIFI_STATE",      "android.hardware.wifi");
        map.put("android.permission.NFC",                    "android.hardware.nfc");
        map.put("android.permission.RECORD_AUDIO",           "android.hardware.microphone");
        map.put("android.permission.USE_SIP",                "android.hardware.sip.voip");
        map.put("android.permission.ACCESS_FINE_LOCATION",   "android.hardware.location.gps");
        map.put("android.permission.ACCESS_MOCK_LOCATION",   "android.hardware.location");
        map.put("android.permission.ACCESS_COARSE_LOCATION", "android.hardware.location.network");
        map.put("android.permission.ACCESS_LOCATION_EXTRA_COMMANDS", "android.hardware.location");
        map.put("android.permission.CALL_PHONE",             "android.hardware.telephony");
        map.put("android.permission.CALL_PRIVILEGED",        "android.hardware.telephony");
        map.put("android.permission.MODIFY_PHONE_STATE",     "android.hardware.telephony");
        map.put("android.permission.PROCESS_OUTGOING_CALLS", "android.hardware.telephony");
        map.put("android.permission.READ_SMS",               "android.hardware.telephony");
        map.put("android.permission.RECEIVE_SMS",            "android.hardware.telephony");
        map.put("android.permission.RECEIVE_MMS",            "android.hardware.telephony");
        map.put("android.permission.RECEIVE_WAP_PUSH",       "android.hardware.telephony");
        map.put("android.permission.SEND_SMS",               "android.hardware.telephony");
        map.put("android.permission.WRITE_APN_SETTINGS",     "android.hardware.telephony");
        map.put("android.permission.WRITE_SMS",              "android.hardware.telephony");
        PERMISSION_TO_FEATURE = Collections.unmodifiableMap(map);
    }

    /** Set of features that are unsupported on TV */
    private static final Set<String> UNSUPPORTED_TV_HARDWARE_FEATURES = new HashSet<>(Arrays.asList(
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
            "android.hardware.sip.voip",
            "android.hardware.telephony",
            "android.hardware.telephony.cdma",
            "android.hardware.telephony.gsm",
            "android.hardware.touchscreen",
            "android.hardware.touchscreen.multitouch",
            "android.hardware.touchscreen.multitouch.distinct",
            "android.hardware.touchscreen.multitouch.jazzhand",
            "android.hardware.wifi"
    ));

    // Tracks declared uses-feature elements with required="false"
    private final Set<String> mNotRequiredFeatures = new HashSet<>();
    // Tracks permissions that imply unsupported hardware features
    // Maps permission name -> (element, implied feature)
    private final Map<String, String> mImpliedUnsupportedFeatures = new LinkedHashMap<>();
    // Tracks permission elements for location reporting
    private final Map<String, Element> mPermissionElements = new LinkedHashMap<>();

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                NODE_USES_PERMISSION,
                "uses-permission-sdk-23",
                NODE_USES_FEATURE
        );
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tagName = element.getTagName();

        if (NODE_USES_FEATURE.equals(tagName)) {
            // Check if this feature is declared with required="false"
            Attr nameAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME);
            if (nameAttr != null) {
                String featureName = nameAttr.getValue();
                Attr requiredAttr = element.getAttributeNodeNS(ANDROID_URI, "required");
                if (requiredAttr != null && VALUE_FALSE.equals(requiredAttr.getValue())) {
                    mNotRequiredFeatures.add(featureName);
                }
            }
        } else if (NODE_USES_PERMISSION.equals(tagName) || "uses-permission-sdk-23".equals(tagName)) {
            Attr nameAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME);
            if (nameAttr != null) {
                String permissionName = nameAttr.getValue();
                String impliedFeature = PERMISSION_TO_FEATURE.get(permissionName);
                if (impliedFeature != null && UNSUPPORTED_TV_HARDWARE_FEATURES.contains(impliedFeature)) {
                    mImpliedUnsupportedFeatures.put(permissionName, impliedFeature);
                    mPermissionElements.put(permissionName, element);
                }
            }
        }
    }

    @Override
    public void afterCheckFile(Context context) {
        // After processing the whole manifest, report permissions whose implied features
        // are not declared as not-required
        for (Map.Entry<String, String> entry : mImpliedUnsupportedFeatures.entrySet()) {
            String permission = entry.getKey();
            String feature = entry.getValue();
            if (!mNotRequiredFeatures.contains(feature)) {
                Element element = mPermissionElements.get(permission);
                if (element != null && context instanceof XmlContext) {
                    XmlContext xmlContext = (XmlContext) context;
                    String message = String.format(
                            "Permission `%1$s` implies hardware feature `%2$s` which is not " +
                            "supported by TV. Consider adding " +
                            "`<uses-feature android:name=\"%2$s\" android:required=\"false\">` " +
                            "to your manifest.",
                            permission, feature);
                    xmlContext.report(
                            PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE,
                            element,
                            xmlContext.getLocation(element),
                            message);
                }
            }
        }
        // Clear state for next file
        mNotRequiredFeatures.clear();
        mImpliedUnsupportedFeatures.clear();
        mPermissionElements.clear();
    }
}
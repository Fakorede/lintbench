package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_REQUIRED;
import static com.android.SdkConstants.TAG_USES_FEATURE;
import static com.android.SdkConstants.TAG_USES_PERMISSION;
import static com.android.SdkConstants.VALUE_FALSE;

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
            Severity.WARNING,
            new Implementation(
                    AndroidTvDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    ).addMoreInfo(
            "https://developer.android.com/guide/topics/manifest/uses-feature-element.html#permissions"
    );

    /**
     * Map from permission name to the hardware feature it implies.
     * Based on the Android documentation for uses-feature permissions.
     */
    private static final Map<String, String> PERMISSION_TO_FEATURE;

    static {
        Map<String, String> map = new HashMap<>();
        map.put("android.permission.BLUETOOTH",                  "android.hardware.bluetooth");
        map.put("android.permission.BLUETOOTH_ADMIN",            "android.hardware.bluetooth");
        map.put("android.permission.CAMERA",                     "android.hardware.camera");
        map.put("android.permission.FLASHLIGHT",                 "android.hardware.camera.flash");
        map.put("android.permission.ACCESS_FINE_LOCATION",       "android.hardware.location.gps");
        map.put("android.permission.ACCESS_MOCK_LOCATION",       "android.hardware.location");
        map.put("android.permission.ACCESS_COARSE_LOCATION",     "android.hardware.location.network");
        map.put("android.permission.ACCESS_LOCATION_EXTRA_COMMANDS", "android.hardware.location");
        map.put("android.permission.INTERNET",                   "android.hardware.location.network");
        map.put("android.permission.CALL_PHONE",                 "android.hardware.telephony");
        map.put("android.permission.CALL_PRIVILEGED",            "android.hardware.telephony");
        map.put("android.permission.MODIFY_PHONE_STATE",         "android.hardware.telephony");
        map.put("android.permission.PROCESS_OUTGOING_CALLS",     "android.hardware.telephony");
        map.put("android.permission.READ_SMS",                   "android.hardware.telephony");
        map.put("android.permission.RECEIVE_SMS",                "android.hardware.telephony");
        map.put("android.permission.RECEIVE_MMS",                "android.hardware.telephony");
        map.put("android.permission.RECEIVE_WAP_PUSH",           "android.hardware.telephony");
        map.put("android.permission.SEND_SMS",                   "android.hardware.telephony");
        map.put("android.permission.WRITE_APN_SETTINGS",         "android.hardware.telephony");
        map.put("android.permission.WRITE_SMS",                  "android.hardware.telephony");
        map.put("android.permission.RECORD_AUDIO",               "android.hardware.microphone");
        PERMISSION_TO_FEATURE = Collections.unmodifiableMap(map);
    }

    /** Features that are not supported on TV */
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
            "android.hardware.location.gps",
            "android.hardware.location.network",
            "android.hardware.microphone",
            "android.hardware.nfc",
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

    /** Set of permissions found in the manifest */
    private final Set<String> mUsesPermissions = new HashSet<>();

    /**
     * Map from feature name to whether it is explicitly declared with required="false".
     * Key: feature name, Value: true if required="false", false if required (or not specified).
     */
    private final Map<String, Boolean> mUsesFeatures = new HashMap<>();

    /** The manifest element (root) */
    private Element mManifestElement;

    /** Location map for uses-permission elements */
    private final Map<String, Attr> mPermissionLocations = new HashMap<>();

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_USES_PERMISSION, TAG_USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(Context context) {
        mUsesPermissions.clear();
        mUsesFeatures.clear();
        mPermissionLocations.clear();
        mManifestElement = null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tagName = element.getTagName();
        Attr nameAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME);
        if (nameAttr == null) {
            return;
        }
        String name = nameAttr.getValue();

        if (TAG_USES_PERMISSION.equals(tagName)) {
            mUsesPermissions.add(name);
            mPermissionLocations.put(name, nameAttr);
        } else if (TAG_USES_FEATURE.equals(tagName)) {
            Attr requiredAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_REQUIRED);
            boolean requiredFalse = requiredAttr != null && VALUE_FALSE.equals(requiredAttr.getValue());
            // If already declared as required=false, keep that; otherwise mark as required
            if (!mUsesFeatures.containsKey(name) || !mUsesFeatures.get(name)) {
                mUsesFeatures.put(name, requiredFalse);
            }
        }
    }

    @Override
    public void afterCheckFile(Context context) {
        if (!(context instanceof XmlContext)) {
            return;
        }
        XmlContext xmlContext = (XmlContext) context;

        for (String permission : mUsesPermissions) {
            String impliedFeature = PERMISSION_TO_FEATURE.get(permission);
            if (impliedFeature == null) {
                continue;
            }

            // Only report if the implied feature is unsupported on TV
            if (!UNSUPPORTED_TV_HARDWARE_FEATURES.contains(impliedFeature)) {
                continue;
            }

            // Check if the feature is explicitly declared with required="false"
            Boolean declaredAsNotRequired = mUsesFeatures.get(impliedFeature);
            if (declaredAsNotRequired != null && declaredAsNotRequired) {
                // Feature is declared with required="false", no issue
                continue;
            }

            // Report the issue
            Attr permissionAttr = mPermissionLocations.get(permission);
            String message = String.format(
                    "Permission `%1$s` implies hardware feature `%2$s` which is not supported by " +
                    "all Android TV devices. Consider adding `<uses-feature " +
                    "android:name=\"%2$s\" android:required=\"false\">` to the manifest.",
                    permission, impliedFeature);

            if (permissionAttr != null) {
                xmlContext.report(
                        PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE,
                        permissionAttr.getOwnerElement(),
                        xmlContext.getLocation(permissionAttr),
                        message);
            }
        }
    }
}
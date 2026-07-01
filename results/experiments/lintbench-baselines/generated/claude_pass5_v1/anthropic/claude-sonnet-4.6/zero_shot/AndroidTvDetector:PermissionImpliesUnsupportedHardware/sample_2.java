package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_REQUIRED;
import static com.android.SdkConstants.TAG_USES_FEATURE;
import static com.android.SdkConstants.TAG_USES_PERMISSION;
import static com.android.SdkConstants.VALUE_FALSE;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final Implementation IMPLEMENTATION = new Implementation(
            AndroidTvDetector.class,
            Scope.MANIFEST_SCOPE
    );

    public static final Issue PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE = Issue.create(
            "PermissionImpliesUnsupportedHardware",
            "Permission Implies Unsupported Hardware",
            "The `<uses-permission>` element should not require a permission that implies an " +
            "unsupported TV hardware feature. Google Play assumes that certain hardware related " +
            "permissions indicate that the underlying hardware features are required by default. " +
            "To fix the issue, consider declaring the corresponding `uses-feature` element with " +
            "`required=\"false\"` attribute.",
            Category.CORRECTNESS,
            3,
            Severity.WARNING,
            IMPLEMENTATION
    ).addMoreInfo(
            "https://developer.android.com/guide/topics/manifest/uses-feature-element.html#permissions"
    );

    /**
     * Map from permission name to the hardware feature it implies.
     * Based on https://developer.android.com/guide/topics/manifest/uses-feature-element.html#permissions
     */
    private static final Map<String, String> PERMISSION_TO_FEATURE;

    static {
        Map<String, String> map = new HashMap<>();
        map.put("android.permission.BLUETOOTH",                  "android.hardware.bluetooth");
        map.put("android.permission.BLUETOOTH_ADMIN",            "android.hardware.bluetooth");
        map.put("android.permission.CAMERA",                     "android.hardware.camera");
        map.put("android.permission.CHANGE_WIFI_MULTICAST_STATE","android.hardware.wifi");
        map.put("android.permission.CHANGE_WIFI_STATE",          "android.hardware.wifi");
        map.put("android.permission.ACCESS_WIFI_STATE",          "android.hardware.wifi");
        map.put("android.permission.ACCESS_FINE_LOCATION",       "android.hardware.location.gps");
        map.put("android.permission.ACCESS_MOCK_LOCATION",       "android.hardware.location");
        map.put("android.permission.ACCESS_COARSE_LOCATION",     "android.hardware.location.network");
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
            "android.hardware.telephony",
            "android.hardware.telephony.cdma",
            "android.hardware.telephony.gsm",
            "android.hardware.touchscreen",
            "android.hardware.touchscreen.multitouch",
            "android.hardware.touchscreen.multitouch.distinct",
            "android.hardware.touchscreen.multitouch.jazzhand",
            "android.hardware.usb.accessory",
            "android.hardware.usb.host",
            "android.hardware.wifi"
    ));

    /** Features declared with required="false" in the manifest */
    private Set<String> mFeaturesDeclaredNotRequired;

    /** Permissions that imply unsupported TV hardware features */
    private final List<Element> mUsesPermissionElements = new ArrayList<>();

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_USES_PERMISSION, TAG_USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mFeaturesDeclaredNotRequired = new HashSet<>();
        mUsesPermissionElements.clear();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if (TAG_USES_FEATURE.equals(tagName)) {
            handleUsesFeature(element);
        } else if (TAG_USES_PERMISSION.equals(tagName)) {
            handleUsesPermission(element);
        }
    }

    private void handleUsesFeature(@NonNull Element element) {
        Attr nameAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME);
        if (nameAttr == null) {
            return;
        }
        String featureName = nameAttr.getValue();
        if (featureName == null || featureName.isEmpty()) {
            return;
        }

        Attr requiredAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_REQUIRED);
        if (requiredAttr != null && VALUE_FALSE.equals(requiredAttr.getValue())) {
            mFeaturesDeclaredNotRequired.add(featureName);
        }
    }

    private void handleUsesPermission(@NonNull Element element) {
        mUsesPermissionElements.add(element);
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        // Now check all uses-permission elements against the features declared as not required
        for (Element permissionElement : mUsesPermissionElements) {
            Attr nameAttr = permissionElement.getAttributeNodeNS(ANDROID_URI, ATTR_NAME);
            if (nameAttr == null) {
                continue;
            }
            String permissionName = nameAttr.getValue();
            if (permissionName == null || permissionName.isEmpty()) {
                continue;
            }

            String impliedFeature = PERMISSION_TO_FEATURE.get(permissionName);
            if (impliedFeature == null) {
                continue;
            }

            // Check if this implied feature is an unsupported TV hardware feature
            if (!UNSUPPORTED_TV_HARDWARE_FEATURES.contains(impliedFeature)) {
                continue;
            }

            // Check if the feature has been declared with required="false"
            if (mFeaturesDeclaredNotRequired.contains(impliedFeature)) {
                continue;
            }

            // Report the issue
            XmlContext xmlContext = (XmlContext) context;
            String message = String.format(
                    "Permission `%1$s` implies feature `%2$s` which is not supported by all " +
                    "Android TV devices. Consider adding `<uses-feature android:name=\"%2$s\" " +
                    "android:required=\"false\">` to the manifest.",
                    permissionName, impliedFeature);
            xmlContext.report(
                    PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE,
                    permissionElement,
                    xmlContext.getLocation(nameAttr),
                    message
            );
        }
    }
}
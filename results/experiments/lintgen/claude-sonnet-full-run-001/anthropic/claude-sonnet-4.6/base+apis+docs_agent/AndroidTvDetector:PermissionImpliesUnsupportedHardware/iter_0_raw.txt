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
            Severity.ERROR,
            new Implementation(
                    AndroidTvDetector.class,
                    Scope.MANIFEST_SCOPE
            ))
            .addMoreInfo(
                    "https://developer.android.com/guide/topics/manifest/uses-feature-element.html#permissions");

    /**
     * Map from permission name to the hardware feature it implies.
     * Based on https://developer.android.com/guide/topics/manifest/uses-feature-element.html#permissions
     */
    private static final Map<String, String> PERMISSION_TO_FEATURE;

    static {
        Map<String, String> map = new HashMap<>();
        map.put("android.permission.BLUETOOTH",               "android.hardware.bluetooth");
        map.put("android.permission.BLUETOOTH_ADMIN",         "android.hardware.bluetooth");
        map.put("android.permission.CAMERA",                  "android.hardware.camera");
        map.put("android.permission.CHANGE_WIFI_MULTICAST_STATE", "android.hardware.wifi");
        map.put("android.permission.CHANGE_WIFI_STATE",       "android.hardware.wifi");
        map.put("android.permission.ACCESS_WIFI_STATE",       "android.hardware.wifi");
        map.put("android.permission.NFC",                     "android.hardware.nfc");
        map.put("android.permission.RECORD_AUDIO",            "android.hardware.microphone");
        map.put("android.permission.USE_SIP",                 "android.hardware.sip.voip");
        map.put("android.permission.ACCESS_FINE_LOCATION",    "android.hardware.location.gps");
        map.put("android.permission.ACCESS_MOCK_LOCATION",    "android.hardware.location");
        map.put("android.permission.ACCESS_COARSE_LOCATION",  "android.hardware.location.network");
        map.put("android.permission.ACCESS_LOCATION_EXTRA_COMMANDS", "android.hardware.location");
        map.put("android.permission.INSTALL_LOCATION_PROVIDER", "android.hardware.location");
        map.put("android.permission.INTERNET",                "android.hardware.wifi");
        map.put("android.permission.CALL_PHONE",              "android.hardware.telephony");
        map.put("android.permission.CALL_PRIVILEGED",         "android.hardware.telephony");
        map.put("android.permission.MODIFY_PHONE_STATE",      "android.hardware.telephony");
        map.put("android.permission.PROCESS_OUTGOING_CALLS",  "android.hardware.telephony");
        map.put("android.permission.READ_SMS",                "android.hardware.telephony");
        map.put("android.permission.RECEIVE_SMS",             "android.hardware.telephony");
        map.put("android.permission.RECEIVE_MMS",             "android.hardware.telephony");
        map.put("android.permission.RECEIVE_WAP_PUSH",        "android.hardware.telephony");
        map.put("android.permission.SEND_SMS",                "android.hardware.telephony");
        map.put("android.permission.WRITE_APN_SETTINGS",      "android.hardware.telephony");
        map.put("android.permission.WRITE_SMS",               "android.hardware.telephony");
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

    /** Set of features that are explicitly declared as not required in the manifest */
    private final Set<String> mExplicitlyNotRequiredFeatures = new HashSet<>();

    /** Map from permission name to the XML element where it was declared */
    private final Map<String, Element> mUsesPermissions = new HashMap<>();

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_USES_PERMISSION, TAG_USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(Context context) {
        mExplicitlyNotRequiredFeatures.clear();
        mUsesPermissions.clear();
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tagName = element.getTagName();
        Attr nameAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME);
        if (nameAttr == null) {
            return;
        }
        String name = nameAttr.getValue();

        if (TAG_USES_FEATURE.equals(tagName)) {
            // Check if this feature is declared with required="false"
            Attr requiredAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_REQUIRED);
            if (requiredAttr != null && VALUE_FALSE.equals(requiredAttr.getValue())) {
                mExplicitlyNotRequiredFeatures.add(name);
            }
        } else if (TAG_USES_PERMISSION.equals(tagName)) {
            mUsesPermissions.put(name, element);
        }
    }

    @Override
    public void afterCheckFile(Context context) {
        // Now check all uses-permission elements to see if they imply unsupported hardware
        // that hasn't been explicitly marked as not required
        for (Map.Entry<String, Element> entry : mUsesPermissions.entrySet()) {
            String permission = entry.getKey();
            Element element = entry.getValue();

            String impliedFeature = PERMISSION_TO_FEATURE.get(permission);
            if (impliedFeature == null) {
                continue;
            }

            // Only report if the implied feature is unsupported on TV
            if (!UNSUPPORTED_TV_HARDWARE_FEATURES.contains(impliedFeature)) {
                continue;
            }

            // Only report if the feature hasn't been explicitly declared as not required
            if (mExplicitlyNotRequiredFeatures.contains(impliedFeature)) {
                continue;
            }

            // Report the issue
            if (context instanceof XmlContext) {
                XmlContext xmlContext = (XmlContext) context;
                xmlContext.report(
                        PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE,
                        element,
                        xmlContext.getLocation(element),
                        String.format(
                                "Permission `%1$s` implies unsupported TV hardware feature " +
                                "`%2$s`. Consider adding `<uses-feature android:name=\"%2$s\" " +
                                "android:required=\"false\">` to the manifest.",
                                permission,
                                impliedFeature
                        )
                );
            }
        }
    }
}
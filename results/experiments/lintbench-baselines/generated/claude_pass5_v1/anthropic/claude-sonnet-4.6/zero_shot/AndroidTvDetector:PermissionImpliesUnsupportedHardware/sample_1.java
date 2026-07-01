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
            5,
            Severity.WARNING,
            IMPLEMENTATION
    ).addMoreInfo("https://developer.android.com/guide/topics/manifest/uses-feature-element.html#permissions");

    // Mapping from permission to the hardware feature it implies
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
        map.put("android.permission.INTERNET",                   null); // no implied feature
        map.put("android.permission.NFC",                        "android.hardware.nfc");
        map.put("android.permission.RECORD_AUDIO",               "android.hardware.microphone");
        map.put("android.permission.USE_SIP",                    "android.hardware.sip.voip");
        map.put("android.permission.CALL_PHONE",                 "android.hardware.telephony");
        map.put("android.permission.CALL_PRIVILEGED",            "android.hardware.telephony");
        map.put("android.permission.MODIFY_PHONE_STATE",         "android.hardware.telephony");
        map.put("android.permission.PROCESS_INCOMING_CALLS",     "android.hardware.telephony");
        map.put("android.permission.READ_CALL_LOG",              "android.hardware.telephony");
        map.put("android.permission.READ_CONTACTS",              "android.hardware.telephony");
        map.put("android.permission.READ_PHONE_STATE",           "android.hardware.telephony");
        map.put("android.permission.READ_SMS",                   "android.hardware.telephony");
        map.put("android.permission.RECEIVE_MMS",                "android.hardware.telephony");
        map.put("android.permission.RECEIVE_SMS",                "android.hardware.telephony");
        map.put("android.permission.RECEIVE_WAP_PUSH",           "android.hardware.telephony");
        map.put("android.permission.SEND_SMS",                   "android.hardware.telephony");
        map.put("android.permission.WRITE_APN_SETTINGS",         "android.hardware.telephony");
        map.put("android.permission.WRITE_CALL_LOG",             "android.hardware.telephony");
        map.put("android.permission.WRITE_CONTACTS",             "android.hardware.telephony");
        map.put("android.permission.WRITE_SMS",                  "android.hardware.telephony");
        PERMISSION_TO_FEATURE = Collections.unmodifiableMap(map);
    }

    // Hardware features that are not supported on TV
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
            "android.hardware.nfc",
            "android.hardware.nfc.hce",
            "android.hardware.screen.landscape",
            "android.hardware.screen.portrait",
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
            "android.hardware.microphone",
            "android.hardware.location",
            "android.hardware.sip.voip"
    ));

    private static final String TAG_USES_PERMISSION = "uses-permission";
    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_REQUIRED = "required";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String LEANBACK_FEATURE = "android.software.leanback";

    /** Features that are explicitly declared as not required */
    private final Set<String> mNotRequiredFeatures = new HashSet<>();

    /** Features that are explicitly declared as required */
    private final Set<String> mRequiredFeatures = new HashSet<>();

    /** List of permission elements that imply unsupported hardware features */
    private final List<Element> mPermissionElements = new ArrayList<>();

    /** Map from permission element to the implied feature name */
    private final Map<Element, String> mPermissionToImpliedFeature = new HashMap<>();

    /** Whether this manifest targets TV (has leanback feature) */
    private boolean mHasLeanbackFeature = false;

    public AndroidTvDetector() {
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_USES_PERMISSION, TAG_USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mNotRequiredFeatures.clear();
        mRequiredFeatures.clear();
        mPermissionElements.clear();
        mPermissionToImpliedFeature.clear();
        mHasLeanbackFeature = false;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();

        if (TAG_USES_FEATURE.equals(tagName)) {
            String featureName = element.getAttributeNS(ANDROID_NS, ATTR_NAME);
            if (featureName == null || featureName.isEmpty()) {
                return;
            }

            if (LEANBACK_FEATURE.equals(featureName)) {
                mHasLeanbackFeature = true;
            }

            String requiredValue = element.getAttributeNS(ANDROID_NS, ATTR_REQUIRED);
            if ("false".equals(requiredValue)) {
                mNotRequiredFeatures.add(featureName);
            } else {
                // required is true by default if not specified
                mRequiredFeatures.add(featureName);
            }
        } else if (TAG_USES_PERMISSION.equals(tagName)) {
            String permissionName = element.getAttributeNS(ANDROID_NS, ATTR_NAME);
            if (permissionName == null || permissionName.isEmpty()) {
                return;
            }

            if (PERMISSION_TO_FEATURE.containsKey(permissionName)) {
                String impliedFeature = PERMISSION_TO_FEATURE.get(permissionName);
                if (impliedFeature != null && UNSUPPORTED_TV_HARDWARE_FEATURES.contains(impliedFeature)) {
                    mPermissionElements.add(element);
                    mPermissionToImpliedFeature.put(element, impliedFeature);
                }
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!mHasLeanbackFeature) {
            // Not a TV app, skip checks
            return;
        }

        if (!(context instanceof XmlContext)) {
            return;
        }
        XmlContext xmlContext = (XmlContext) context;

        for (Element permissionElement : mPermissionElements) {
            String impliedFeature = mPermissionToImpliedFeature.get(permissionElement);
            if (impliedFeature == null) {
                continue;
            }

            // Check if the feature has been explicitly declared as not required
            if (mNotRequiredFeatures.contains(impliedFeature)) {
                // Properly handled
                continue;
            }

            // The permission implies a hardware feature that is unsupported on TV,
            // and that feature is not declared as not required.
            String permissionName = permissionElement.getAttributeNS(ANDROID_NS, ATTR_NAME);
            String message = String.format(
                    "Permission `%1$s` implies unsupported TV hardware feature " +
                    "`%2$s`. Consider declaring `<uses-feature android:name=\"%2$s\" " +
                    "android:required=\"false\">` explicitly.",
                    permissionName, impliedFeature);

            Attr permAttr = permissionElement.getAttributeNodeNS(ANDROID_NS, ATTR_NAME);
            if (permAttr != null) {
                xmlContext.report(PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE,
                        permissionElement,
                        xmlContext.getValueLocation(permAttr),
                        message);
            } else {
                xmlContext.report(PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE,
                        permissionElement,
                        xmlContext.getLocation(permissionElement),
                        message);
            }
        }
    }
}
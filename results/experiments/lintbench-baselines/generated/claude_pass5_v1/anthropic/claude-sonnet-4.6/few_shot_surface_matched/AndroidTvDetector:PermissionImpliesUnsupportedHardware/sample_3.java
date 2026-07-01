package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_REQUIRED;
import static com.android.xml.AndroidManifest.NODE_USES_FEATURE;
import static com.android.xml.AndroidManifest.NODE_USES_PERMISSION;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE =
            Issue.create(
                    "PermissionImpliesUnsupportedHardware",
                    "Permission Implies Unsupported Hardware",
                    "The `<uses-permission>` element should not require a permission that implies "
                            + "an unsupported TV hardware feature. Google Play assumes that certain "
                            + "hardware related permissions indicate that the underlying hardware "
                            + "features are required by default. To fix the issue, consider "
                            + "declaring the corresponding `uses-feature` element with "
                            + "`required=\"false\"` attribute.",
                    Category.CORRECTNESS,
                    7,
                    Severity.WARNING,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    /**
     * Mapping from permission name to the hardware feature it implies,
     * based on https://developer.android.com/guide/topics/manifest/uses-feature-element.html#permissions
     */
    private static final Map<String, String> PERMISSION_TO_FEATURE = new HashMap<>();

    static {
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
    }

    /** Features that are not supported on Android TV */
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

    /**
     * Set of features that are explicitly declared with required="false" in the manifest.
     * Populated during visitElement for uses-feature elements.
     */
    private Set<String> mExplicitlyNotRequiredFeatures;

    /**
     * Map from permission name to the element that declared it, for permissions
     * that imply unsupported hardware features.
     * Populated during visitElement for uses-permission elements.
     */
    private Map<String, Element> mProblematicPermissions;

    /**
     * Map from permission name to the XmlContext, stored so we can report after the file is parsed.
     */
    private XmlContext mContext;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(NODE_USES_PERMISSION, NODE_USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(@NonNull com.android.tools.lint.detector.api.Context context) {
        mExplicitlyNotRequiredFeatures = new HashSet<>();
        mProblematicPermissions = new HashMap<>();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        mContext = context;
        String tagName = element.getTagName();

        if (NODE_USES_FEATURE.equals(tagName)) {
            // Check if this feature is declared with required="false"
            String featureName = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (featureName != null && !featureName.isEmpty()) {
                String required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
                if ("false".equals(required)) {
                    mExplicitlyNotRequiredFeatures.add(featureName);
                }
            }
        } else if (NODE_USES_PERMISSION.equals(tagName)) {
            // Check if this permission implies an unsupported TV hardware feature
            String permissionName = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (permissionName != null && !permissionName.isEmpty()) {
                String impliedFeature = PERMISSION_TO_FEATURE.get(permissionName);
                if (impliedFeature != null && UNSUPPORTED_TV_HARDWARE_FEATURES.contains(impliedFeature)) {
                    mProblematicPermissions.put(permissionName, element);
                }
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull com.android.tools.lint.detector.api.Context context) {
        if (mProblematicPermissions == null || mContext == null) {
            return;
        }

        for (Map.Entry<String, Element> entry : mProblematicPermissions.entrySet()) {
            String permissionName = entry.getKey();
            Element element = entry.getValue();
            String impliedFeature = PERMISSION_TO_FEATURE.get(permissionName);

            // Only report if the implied feature is not explicitly declared as not required
            if (impliedFeature != null && !mExplicitlyNotRequiredFeatures.contains(impliedFeature)) {
                String message = String.format(
                        "Permission exists without corresponding hardware `<uses-feature "
                                + "android:name=\"%1$s\" required=\"false\">` tag",
                        impliedFeature);
                mContext.report(
                        PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE,
                        element,
                        mContext.getLocation(element),
                        message);
            }
        }

        mExplicitlyNotRequiredFeatures = null;
        mProblematicPermissions = null;
        mContext = null;
    }
}
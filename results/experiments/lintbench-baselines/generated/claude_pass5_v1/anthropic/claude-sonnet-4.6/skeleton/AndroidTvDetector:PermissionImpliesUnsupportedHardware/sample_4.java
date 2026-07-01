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
    private static final String TAG_MANIFEST = "manifest";
    private static final String ATTR_NAME = "android:name";
    private static final String ATTR_REQUIRED = "android:required";

    /**
     * Map from permission name to the hardware feature it implies.
     * Based on https://developer.android.com/guide/topics/manifest/uses-feature-element.html#permissions
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

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE =
            Issue.create(
                    "PermissionImpliesUnsupportedHardware",
                    "Permission Implies Unsupported Hardware",
                    "The `<uses-permission>` element should not require a permission that "
                            + "implies an unsupported TV hardware feature. Google Play assumes "
                            + "that certain hardware related permissions indicate that the "
                            + "underlying hardware features are required by default. To fix "
                            + "the issue, consider declaring the corresponding `uses-feature` "
                            + "element with `required=\"false\"` attribute.",
                    Category.CORRECTNESS,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    // Keep ISSUE as an alias for compatibility with the skeleton
    public static final Issue ISSUE = PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE;

    /** Set of declared features with required="false" in the current manifest */
    private Set<String> mOptionalFeatures;

    /** Map of permission elements that imply hardware features, for deferred reporting */
    private Map<Element, String> mPermissionToFeatureElements;

    /** The XmlContext for the current file */
    private XmlContext mContext;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_USES_PERMISSION, TAG_USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mOptionalFeatures = new HashSet<>();
        mPermissionToFeatureElements = new HashMap<>();
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mPermissionToFeatureElements == null || mPermissionToFeatureElements.isEmpty()) {
            return;
        }

        // For each permission that implies a hardware feature, check if the feature
        // has been declared as optional (required="false")
        for (Map.Entry<Element, String> entry : mPermissionToFeatureElements.entrySet()) {
            Element permissionElement = entry.getKey();
            String impliedFeature = entry.getValue();

            if (mOptionalFeatures == null || !mOptionalFeatures.contains(impliedFeature)) {
                // The implied feature is not declared as optional — report the issue
                String permissionName = permissionElement.getAttributeNS(
                        "http://schemas.android.com/apk/res/android", "name");
                if (permissionName == null || permissionName.isEmpty()) {
                    permissionName = permissionElement.getAttribute("android:name");
                }

                String message = String.format(
                        "Permission exists without corresponding hardware `<uses-feature "
                                + "android:name=\"%1$s\" required=\"false\">` tag",
                        impliedFeature);

                if (mContext != null) {
                    mContext.report(
                            PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE,
                            permissionElement,
                            mContext.getLocation(permissionElement),
                            message);
                }
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        mContext = context;
        String tagName = element.getTagName();

        if (TAG_USES_FEATURE.equals(tagName)) {
            // Check if this feature is declared with required="false"
            String required = element.getAttributeNS(
                    "http://schemas.android.com/apk/res/android", "required");
            if (required == null || required.isEmpty()) {
                required = element.getAttribute("android:required");
            }

            if ("false".equals(required)) {
                String featureName = element.getAttributeNS(
                        "http://schemas.android.com/apk/res/android", "name");
                if (featureName == null || featureName.isEmpty()) {
                    featureName = element.getAttribute("android:name");
                }
                if (featureName != null && !featureName.isEmpty()) {
                    if (mOptionalFeatures == null) {
                        mOptionalFeatures = new HashSet<>();
                    }
                    mOptionalFeatures.add(featureName);
                }
            }
        } else if (TAG_USES_PERMISSION.equals(tagName)) {
            // Check if this permission implies a hardware feature
            String permissionName = element.getAttributeNS(
                    "http://schemas.android.com/apk/res/android", "name");
            if (permissionName == null || permissionName.isEmpty()) {
                permissionName = element.getAttribute("android:name");
            }

            if (permissionName != null && !permissionName.isEmpty()) {
                String impliedFeature = PERMISSION_TO_FEATURE.get(permissionName);
                if (impliedFeature != null) {
                    if (mPermissionToFeatureElements == null) {
                        mPermissionToFeatureElements = new HashMap<>();
                    }
                    mPermissionToFeatureElements.put(element, impliedFeature);
                }
            }
        }
    }
}
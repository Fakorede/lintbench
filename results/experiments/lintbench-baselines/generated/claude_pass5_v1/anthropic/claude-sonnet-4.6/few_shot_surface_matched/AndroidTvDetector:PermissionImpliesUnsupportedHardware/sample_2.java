package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
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
     * Map from permission name to the hardware feature it implies.
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
                "android.permission.FLASHLIGHT",
                "android.hardware.camera.flash");
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
        PERMISSION_TO_FEATURE.put(
                "android.permission.ACCESS_COARSE_LOCATION",
                "android.hardware.location.network");
        PERMISSION_TO_FEATURE.put(
                "android.permission.ACCESS_FINE_LOCATION",
                "android.hardware.location.gps");
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
                "android.permission.INTERNET",
                "android.hardware.wifi");
        PERMISSION_TO_FEATURE.put(
                "android.permission.ACCESS_WIFI_STATE",
                "android.hardware.wifi");
        PERMISSION_TO_FEATURE.put(
                "android.permission.CHANGE_WIFI_STATE",
                "android.hardware.wifi");
        PERMISSION_TO_FEATURE.put(
                "android.permission.CHANGE_WIFI_MULTICAST_STATE",
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
                "android.permission.VIBRATE",
                "android.hardware.vibrator");
    }

    /** Permissions declared in this manifest file */
    private final Set<String> mDeclaredPermissions = new HashSet<>();

    /**
     * Map from implied feature to the element that declared the permission implying it,
     * for permissions that imply unsupported hardware features.
     */
    private final Map<String, Element> mImpliedFeatures = new HashMap<>();

    /**
     * Set of features that have been explicitly declared with required="false".
     */
    private final Set<String> mExplicitlyOptionalFeatures = new HashSet<>();

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(NODE_USES_PERMISSION, NODE_USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(@NonNull com.android.tools.lint.detector.api.Context context) {
        mDeclaredPermissions.clear();
        mImpliedFeatures.clear();
        mExplicitlyOptionalFeatures.clear();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();

        if (NODE_USES_PERMISSION.equals(tagName)) {
            String name = element.getAttributeNS(ANDROID_URI, "name");
            if (name != null && !name.isEmpty()) {
                String impliedFeature = PERMISSION_TO_FEATURE.get(name);
                if (impliedFeature != null) {
                    mImpliedFeatures.put(impliedFeature, element);
                }
            }
        } else if (NODE_USES_FEATURE.equals(tagName)) {
            String name = element.getAttributeNS(ANDROID_URI, "name");
            if (name != null && !name.isEmpty()) {
                String requiredAttr = element.getAttributeNS(ANDROID_URI, "required");
                if ("false".equals(requiredAttr)) {
                    mExplicitlyOptionalFeatures.add(name);
                }
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull com.android.tools.lint.detector.api.Context context) {
        if (!(context instanceof XmlContext)) {
            return;
        }
        XmlContext xmlContext = (XmlContext) context;

        for (Map.Entry<String, Element> entry : mImpliedFeatures.entrySet()) {
            String feature = entry.getKey();
            Element permissionElement = entry.getValue();

            if (!mExplicitlyOptionalFeatures.contains(feature)) {
                String permissionName = permissionElement.getAttributeNS(ANDROID_URI, "name");
                xmlContext.report(
                        PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE,
                        permissionElement,
                        xmlContext.getLocation(permissionElement),
                        String.format(
                                "Permission `%1$s` implies hardware feature `%2$s` which is not "
                                        + "supported by TV. Consider adding "
                                        + "`<uses-feature android:name=\"%2$s\" "
                                        + "android:required=\"false\">` to your manifest.",
                                permissionName,
                                feature));
            }
        }
    }
}
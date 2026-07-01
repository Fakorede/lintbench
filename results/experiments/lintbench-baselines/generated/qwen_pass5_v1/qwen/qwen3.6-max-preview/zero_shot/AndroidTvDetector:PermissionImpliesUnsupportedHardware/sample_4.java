package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.*;
import org.w3c.dom.Element;
import java.util.*;

public class AndroidTvDetector extends Detector implements XmlScanner {
    public static final Issue ISSUE = Issue.create(
        "PermissionImpliesUnsupportedHardware",
        "Permission implies unsupported TV hardware feature",
        "The `<uses-permission>` element should not require a permission that implies an unsupported TV hardware feature. " +
        "Google Play assumes that certain hardware related permissions indicate that the underlying hardware features are required by default. " +
        "To fix the issue, consider declaring the corresponding `uses-feature` element with `required=\"false\"` attribute.",
        Category.TV,
        6,
        Severity.WARNING,
        new Implementation(AndroidTvDetector.class, EnumSet.of(Scope.MANIFEST))
    );

    private static final Map<String, String> PERMISSION_TO_FEATURE = new HashMap<>();
    static {
        PERMISSION_TO_FEATURE.put("android.permission.BLUETOOTH", "android.hardware.bluetooth");
        PERMISSION_TO_FEATURE.put("android.permission.BLUETOOTH_ADMIN", "android.hardware.bluetooth");
        PERMISSION_TO_FEATURE.put("android.permission.BLUETOOTH_CONNECT", "android.hardware.bluetooth");
        PERMISSION_TO_FEATURE.put("android.permission.BLUETOOTH_SCAN", "android.hardware.bluetooth");
        PERMISSION_TO_FEATURE.put("android.permission.BLUETOOTH_ADVERTISE", "android.hardware.bluetooth");
        PERMISSION_TO_FEATURE.put("android.permission.CAMERA", "android.hardware.camera");
        PERMISSION_TO_FEATURE.put("android.permission.ACCESS_COARSE_LOCATION", "android.hardware.location.network");
        PERMISSION_TO_FEATURE.put("android.permission.ACCESS_FINE_LOCATION", "android.hardware.location.gps");
        PERMISSION_TO_FEATURE.put("android.permission.NFC", "android.hardware.nfc");
        PERMISSION_TO_FEATURE.put("android.permission.RECORD_AUDIO", "android.hardware.microphone");
        PERMISSION_TO_FEATURE.put("android.permission.ACCESS_WIFI_STATE", "android.hardware.wifi");
        PERMISSION_TO_FEATURE.put("android.permission.CHANGE_WIFI_STATE", "android.hardware.wifi");
        PERMISSION_TO_FEATURE.put("android.permission.USE_SIP", "android.hardware.sip");

        String[] telephonyPermissions = {
            "android.permission.CALL_PHONE", "android.permission.CALL_PRIVILEGED",
            "android.permission.MODIFY_PHONE_STATE", "android.permission.PROCESS_OUTGOING_CALLS",
            "android.permission.READ_SMS", "android.permission.RECEIVE_SMS",
            "android.permission.RECEIVE_MMS", "android.permission.RECEIVE_WAP_PUSH",
            "android.permission.SEND_SMS", "android.permission.WRITE_SMS",
            "android.permission.READ_PHONE_STATE", "android.permission.READ_PHONE_NUMBERS",
            "android.permission.ANSWER_PHONE_CALLS"
        };
        for (String p : telephonyPermissions) {
            PERMISSION_TO_FEATURE.put(p, "android.hardware.telephony");
        }
    }

    private final Map<String, Element> permissionElements = new HashMap<>();
    private final Set<String> optionalFeatures = new HashSet<>();

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(SdkConstants.TAG_USES_PERMISSION, SdkConstants.TAG_USES_FEATURE);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();
        if (SdkConstants.TAG_USES_PERMISSION.equals(tag)) {
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if (name != null && !name.isEmpty()) {
                permissionElements.put(name, element);
            }
        } else if (SdkConstants.TAG_USES_FEATURE.equals(tag)) {
            String required = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_REQUIRED);
            if ("false".equals(required)) {
                String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                if (name != null && !name.isEmpty()) {
                    optionalFeatures.add(name);
                }
            }
        }
    }

    @Override
    public void beforeCheckFile(Context context) {
        permissionElements.clear();
        optionalFeatures.clear();
    }

    @Override
    public void afterCheckFile(Context context) {
        if (context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            for (Map.Entry<String, Element> entry : permissionElements.entrySet()) {
                String permission = entry.getKey();
                String feature = PERMISSION_TO_FEATURE.get(permission);
                if (feature != null && !optionalFeatures.contains(feature)) {
                    String message = String.format(
                        "Permission %1$s implies an unsupported TV hardware feature (%2$s). " +
                        "Google Play assumes this hardware feature is required. " +
                        "Consider adding `<uses-feature android:name=\"%2$s\" android:required=\"false\" />` to support Android TV.",
                        permission, feature);
                    xmlContext.report(ISSUE, entry.getValue(), xmlContext.getLocation(entry.getValue()), message);
                }
            }
        }
    }
}
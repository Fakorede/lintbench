package com.android.tools.lint.checks;

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

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String TAG_USES_PERMISSION = "uses-permission";
    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_REQUIRED = "required";

    private static final Map<String, String> PERMISSION_TO_FEATURE_MAP = new HashMap<>();

    static {
        PERMISSION_TO_FEATURE_MAP.put("android.permission.BLUETOOTH", "android.hardware.bluetooth");
        PERMISSION_TO_FEATURE_MAP.put("android.permission.BLUETOOTH_ADMIN", "android.hardware.bluetooth");
        PERMISSION_TO_FEATURE_MAP.put("android.permission.BLUETOOTH_CONNECT", "android.hardware.bluetooth");
        PERMISSION_TO_FEATURE_MAP.put("android.permission.BLUETOOTH_ADVERTISE", "android.hardware.bluetooth");
        PERMISSION_TO_FEATURE_MAP.put("android.permission.BLUETOOTH_SCAN", "android.hardware.bluetooth");
        PERMISSION_TO_FEATURE_MAP.put("android.permission.CAMERA", "android.hardware.camera");
        PERMISSION_TO_FEATURE_MAP.put("android.permission.ACCESS_COARSE_LOCATION", "android.hardware.location.network");
        PERMISSION_TO_FEATURE_MAP.put("android.permission.ACCESS_FINE_LOCATION", "android.hardware.location.gps");
        PERMISSION_TO_FEATURE_MAP.put("android.permission.NFC", "android.hardware.nfc");
        PERMISSION_TO_FEATURE_MAP.put("android.permission.RECORD_AUDIO", "android.hardware.microphone");
        PERMISSION_TO_FEATURE_MAP.put("android.permission.ACCESS_WIFI_STATE", "android.hardware.wifi");
        PERMISSION_TO_FEATURE_MAP.put("android.permission.CHANGE_WIFI_STATE", "android.hardware.wifi");

        String[] telephonyPerms = {
                "android.permission.CALL_PHONE", "android.permission.CALL_PRIVILEGED",
                "android.permission.MODIFY_PHONE_STATE", "android.permission.PROCESS_OUTGOING_CALLS",
                "android.permission.READ_SMS", "android.permission.RECEIVE_SMS",
                "android.permission.RECEIVE_MMS", "android.permission.RECEIVE_WAP_PUSH",
                "android.permission.SEND_SMS", "android.permission.WRITE_APN_SETTINGS",
                "android.permission.WRITE_SMS", "android.permission.READ_PHONE_STATE",
                "android.permission.READ_PHONE_NUMBERS", "android.permission.ANSWER_PHONE_CALLS"
        };
        for (String perm : telephonyPerms) {
            PERMISSION_TO_FEATURE_MAP.put(perm, "android.hardware.telephony");
        }
    }

    public static final Issue ISSUE = Issue.create(
            "PermissionImpliesUnsupportedHardware",
            "Permission Implies Unsupported Hardware",
            "The `<uses-permission>` element should not require a permission that implies an "
                    + "unsupported TV hardware feature. Google Play assumes that certain hardware "
                    + "related permissions indicate that the underlying hardware features are "
                    + "required by default. To fix the issue, consider declaring the corresponding "
                    + "`uses-feature` element with `required=\"false\"` attribute.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private Set<String> optionalFeatures;
    private Map<Element, String> permissionsToCheck;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_USES_PERMISSION, TAG_USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(@NonNull XmlContext context) {
        optionalFeatures = new HashSet<>();
        permissionsToCheck = new HashMap<>();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (TAG_USES_FEATURE.equals(tag)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            String required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
            if (!name.isEmpty() && "false".equals(required)) {
                optionalFeatures.add(name);
            }
        } else if (TAG_USES_PERMISSION.equals(tag)) {
            String permName = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            String impliedFeature = PERMISSION_TO_FEATURE_MAP.get(permName);
            if (impliedFeature != null) {
                permissionsToCheck.put(element, impliedFeature);
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull XmlContext context) {
        for (Map.Entry<Element, String> entry : permissionsToCheck.entrySet()) {
            String impliedFeature = entry.getValue();
            if (!optionalFeatures.contains(impliedFeature)) {
                Element permElement = entry.getKey();
                String permName = permElement.getAttributeNS(ANDROID_URI, ATTR_NAME);
                String message = String.format(
                        "Permission %s implies hardware feature %s, which is not declared as optional. "
                                + "Consider adding `<uses-feature android:name=\"%s\" android:required=\"false\" />` "
                                + "to prevent filtering on TV or other devices.",
                        permName, impliedFeature, impliedFeature);
                context.report(ISSUE, permElement, context.getLocation(permElement), message);
            }
        }
        optionalFeatures = null;
        permissionsToCheck = null;
    }
}
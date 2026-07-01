package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.jetbrains.annotations.NonNull;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AndroidTvDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "PermissionImpliesUnsupportedHardware",
            "Permission implies unsupported TV hardware feature",
            "The `<uses-permission>` element should not require a permission that implies an unsupported TV hardware feature. " +
            "Google Play assumes that certain hardware related permissions indicate that the underlying hardware features are required by default. " +
            "To fix the issue, consider declaring the corresponding `uses-feature` element with `required=\"false\"` attribute.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE)
    );

    private static final Map<String, String> PERMISSION_TO_FEATURE;
    static {
        Map<String, String> map = new HashMap<>();
        map.put("android.permission.ACCESS_COARSE_LOCATION", "android.hardware.location.network");
        map.put("android.permission.ACCESS_FINE_LOCATION", "android.hardware.location.gps");
        map.put("android.permission.BLUETOOTH", "android.hardware.bluetooth");
        map.put("android.permission.BLUETOOTH_ADMIN", "android.hardware.bluetooth");
        map.put("android.permission.BLUETOOTH_CONNECT", "android.hardware.bluetooth");
        map.put("android.permission.BLUETOOTH_SCAN", "android.hardware.bluetooth");
        map.put("android.permission.BLUETOOTH_ADVERTISE", "android.hardware.bluetooth");
        map.put("android.permission.CAMERA", "android.hardware.camera");
        map.put("android.permission.CALL_PHONE", "android.hardware.telephony");
        map.put("android.permission.CALL_PRIVILEGED", "android.hardware.telephony");
        map.put("android.permission.MODIFY_PHONE_STATE", "android.hardware.telephony");
        map.put("android.permission.PROCESS_OUTGOING_CALLS", "android.hardware.telephony");
        map.put("android.permission.READ_SMS", "android.hardware.telephony");
        map.put("android.permission.RECEIVE_SMS", "android.hardware.telephony");
        map.put("android.permission.RECEIVE_MMS", "android.hardware.telephony");
        map.put("android.permission.RECEIVE_WAP_PUSH", "android.hardware.telephony");
        map.put("android.permission.SEND_SMS", "android.hardware.telephony");
        map.put("android.permission.WRITE_APN_SETTINGS", "android.hardware.telephony");
        map.put("android.permission.READ_PHONE_STATE", "android.hardware.telephony");
        map.put("android.permission.NFC", "android.hardware.nfc");
        map.put("android.permission.ACCESS_WIFI_STATE", "android.hardware.wifi");
        map.put("android.permission.CHANGE_WIFI_STATE", "android.hardware.wifi");
        map.put("android.permission.USE_FINGERPRINT", "android.hardware.fingerprint");
        PERMISSION_TO_FEATURE = Collections.unmodifiableMap(map);
    }

    private List<Element> mPermissionElements;
    private Set<String> mOptionalFeatures;

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mPermissionElements = new ArrayList<>();
        mOptionalFeatures = new HashSet<>();
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("uses-permission", "uses-feature");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if ("uses-permission".equals(tag)) {
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI, "name");
            if (name != null && !name.isEmpty() && PERMISSION_TO_FEATURE.containsKey(name)) {
                mPermissionElements.add(element);
            }
        } else if ("uses-feature".equals(tag)) {
            String required = element.getAttributeNS(SdkConstants.ANDROID_URI, "required");
            if ("false".equals(required)) {
                String name = element.getAttributeNS(SdkConstants.ANDROID_URI, "name");
                if (name != null && !name.isEmpty()) {
                    mOptionalFeatures.add(name);
                }
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            for (Element permElement : mPermissionElements) {
                String permName = permElement.getAttributeNS(SdkConstants.ANDROID_URI, "name");
                String feature = PERMISSION_TO_FEATURE.get(permName);
                if (feature != null && !mOptionalFeatures.contains(feature)) {
                    String message = String.format(
                            "Permission %s implies hardware feature %s, which may not be available on Android TV. " +
                            "Add <uses-feature android:name=\"%s\" android:required=\"false\" /> to the manifest.",
                            permName, feature, feature);
                    xmlContext.report(ISSUE, permElement, xmlContext.getLocation(permElement), message);
                }
            }
        }
    }
}
package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class AndroidTvDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "PermissionImpliesUnsupportedHardware",
            "Permission implies unsupported TV hardware feature",
            "The `<uses-permission>` element should not require a permission that implies an unsupported TV hardware feature. " +
            "Google Play assumes that certain hardware related permissions indicate that the underlying hardware features are required by default. " +
            "To fix the issue, consider declaring the corresponding `uses-feature` element with `required=\"false\"` attribute.",
            Category.CORRECTNESS,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE)
    );

    private static final Map<String, String> PERMISSION_TO_FEATURE = new HashMap<>();
    static {
        // Camera
        PERMISSION_TO_FEATURE.put("android.permission.CAMERA", "android.hardware.camera");
        // Location
        PERMISSION_TO_FEATURE.put("android.permission.ACCESS_COARSE_LOCATION", "android.hardware.location.network");
        PERMISSION_TO_FEATURE.put("android.permission.ACCESS_FINE_LOCATION", "android.hardware.location.gps");
        // Bluetooth
        PERMISSION_TO_FEATURE.put("android.permission.BLUETOOTH", "android.hardware.bluetooth");
        PERMISSION_TO_FEATURE.put("android.permission.BLUETOOTH_ADMIN", "android.hardware.bluetooth");
        PERMISSION_TO_FEATURE.put("android.permission.BLUETOOTH_CONNECT", "android.hardware.bluetooth");
        PERMISSION_TO_FEATURE.put("android.permission.BLUETOOTH_SCAN", "android.hardware.bluetooth");
        // NFC
        PERMISSION_TO_FEATURE.put("android.permission.NFC", "android.hardware.nfc");
        // Telephony
        PERMISSION_TO_FEATURE.put("android.permission.CALL_PHONE", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.CALL_PRIVILEGED", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.MODIFY_PHONE_STATE", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.PROCESS_OUTGOING_CALLS", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.READ_SMS", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.RECEIVE_SMS", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.RECEIVE_MMS", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.RECEIVE_WAP_PUSH", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.SEND_SMS", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.WRITE_APN_SETTINGS", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.WRITE_SMS", "android.hardware.telephony");
        // WiFi
        PERMISSION_TO_FEATURE.put("android.permission.ACCESS_WIFI_STATE", "android.hardware.wifi");
        PERMISSION_TO_FEATURE.put("android.permission.CHANGE_WIFI_STATE", "android.hardware.wifi");
        // Microphone
        PERMISSION_TO_FEATURE.put("android.permission.RECORD_AUDIO", "android.hardware.microphone");
        // Biometrics
        PERMISSION_TO_FEATURE.put("android.permission.USE_FINGERPRINT", "android.hardware.fingerprint");
        PERMISSION_TO_FEATURE.put("android.permission.USE_BIOMETRIC", "android.hardware.fingerprint");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("uses-permission");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String permissionName = element.getAttributeNS(ANDROID_URI, "name");
        if (permissionName.isEmpty()) {
            return;
        }

        String impliedFeature = PERMISSION_TO_FEATURE.get(permissionName);
        if (impliedFeature == null) {
            return;
        }

        if (hasFeatureDeclaredAsNotRequired(context, impliedFeature)) {
            return;
        }

        String message = String.format(
            "Permission `%s` implies hardware feature `%s`, which is not supported on all Android TV devices. " +
            "Declare `<uses-feature android:name=\"%s\" android:required=\"false\" />` to support TV devices.",
            permissionName, impliedFeature, impliedFeature);

        context.report(ISSUE, element, context.getLocation(element), message);
    }

    private boolean hasFeatureDeclaredAsNotRequired(XmlContext context, String featureName) {
        if (context.getDocument() == null) {
            return false;
        }
        NodeList features = context.getDocument().getElementsByTagName("uses-feature");
        for (int i = 0; i < features.getLength(); i++) {
            Element featureElement = (Element) features.item(i);
            String name = featureElement.getAttributeNS(ANDROID_URI, "name");
            if (featureName.equals(name)) {
                String required = featureElement.getAttributeNS(ANDROID_URI, "required");
                return "false".equals(required);
            }
        }
        return false;
    }
}
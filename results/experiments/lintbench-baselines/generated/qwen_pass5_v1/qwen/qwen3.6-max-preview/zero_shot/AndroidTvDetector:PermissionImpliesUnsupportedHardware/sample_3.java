package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    private static final Map<String, String> PERMISSION_TO_FEATURE = new HashMap<>();

    static {
        PERMISSION_TO_FEATURE.put("android.permission.CAMERA", "android.hardware.camera");
        PERMISSION_TO_FEATURE.put("android.permission.ACCESS_COARSE_LOCATION", "android.hardware.location.network");
        PERMISSION_TO_FEATURE.put("android.permission.ACCESS_FINE_LOCATION", "android.hardware.location.gps");
        PERMISSION_TO_FEATURE.put("android.permission.BLUETOOTH", "android.hardware.bluetooth");
        PERMISSION_TO_FEATURE.put("android.permission.BLUETOOTH_ADMIN", "android.hardware.bluetooth");
        PERMISSION_TO_FEATURE.put("android.permission.BLUETOOTH_CONNECT", "android.hardware.bluetooth");
        PERMISSION_TO_FEATURE.put("android.permission.BLUETOOTH_SCAN", "android.hardware.bluetooth");
        PERMISSION_TO_FEATURE.put("android.permission.NFC", "android.hardware.nfc");
        PERMISSION_TO_FEATURE.put("android.permission.SEND_SMS", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.RECEIVE_SMS", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.READ_SMS", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.CALL_PHONE", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.READ_PHONE_STATE", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.ACCESS_WIFI_STATE", "android.hardware.wifi");
        PERMISSION_TO_FEATURE.put("android.permission.CHANGE_WIFI_STATE", "android.hardware.wifi");
        PERMISSION_TO_FEATURE.put("android.permission.RECORD_AUDIO", "android.hardware.microphone");
    }

    public static final Issue ISSUE = Issue.create(
            "PermissionImpliesUnsupportedHardware",
            "Permission implies unsupported hardware feature",
            "The `<uses-permission>` element should not require a permission that implies an unsupported TV hardware feature. " +
            "Google Play assumes that certain hardware related permissions indicate that the underlying hardware features are required by default. " +
            "To fix the issue, consider declaring the corresponding `uses-feature` element with `required=\"false\"` attribute.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("uses-permission");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String permissionName = element.getAttributeNS(SdkConstants.ANDROID_URI, "name");
        if (permissionName == null || permissionName.isEmpty()) {
            return;
        }

        String impliedFeature = PERMISSION_TO_FEATURE.get(permissionName);
        if (impliedFeature == null) {
            return;
        }

        if (!hasOptionalFeature(context.document, impliedFeature)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    String.format("Permission `%s` implies hardware feature `%s`, which is not supported on all Android TV devices. " +
                            "Add `<uses-feature android:name=\"%s\" android:required=\"false\" />` to the manifest.",
                            permissionName, impliedFeature, impliedFeature)
            );
        }
    }

    private static boolean hasOptionalFeature(Document document, String featureName) {
        NodeList features = document.getElementsByTagName("uses-feature");
        for (int i = 0; i < features.getLength(); i++) {
            Element featureElement = (Element) features.item(i);
            String name = featureElement.getAttributeNS(SdkConstants.ANDROID_URI, "name");
            String required = featureElement.getAttributeNS(SdkConstants.ANDROID_URI, "required");
            if (featureName.equals(name) && "false".equals(required)) {
                return true;
            }
        }
        return false;
    }
}
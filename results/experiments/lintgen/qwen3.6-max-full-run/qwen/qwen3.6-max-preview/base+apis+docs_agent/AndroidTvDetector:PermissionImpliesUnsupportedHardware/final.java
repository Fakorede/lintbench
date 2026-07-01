package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class AndroidTvDetector extends Detector implements XmlScanner {

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

    private static final Map<String, String> PERMISSION_TO_FEATURE = new HashMap<>();

    static {
        PERMISSION_TO_FEATURE.put("android.permission.BLUETOOTH", "android.hardware.bluetooth");
        PERMISSION_TO_FEATURE.put("android.permission.BLUETOOTH_ADMIN", "android.hardware.bluetooth");
        PERMISSION_TO_FEATURE.put("android.permission.CAMERA", "android.hardware.camera");
        PERMISSION_TO_FEATURE.put("android.permission.ACCESS_COARSE_LOCATION", "android.hardware.location.network");
        PERMISSION_TO_FEATURE.put("android.permission.ACCESS_FINE_LOCATION", "android.hardware.location.gps");
        PERMISSION_TO_FEATURE.put("android.permission.NFC", "android.hardware.nfc");
        PERMISSION_TO_FEATURE.put("android.permission.CALL_PHONE", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.PROCESS_OUTGOING_CALLS", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.READ_PHONE_STATE", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.SEND_SMS", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.RECEIVE_SMS", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.RECEIVE_MMS", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.RECEIVE_WAP_PUSH", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.READ_SMS", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.WRITE_SMS", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.USE_SIP", "android.hardware.sip");
        PERMISSION_TO_FEATURE.put("android.permission.ANSWER_PHONE_CALLS", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.READ_PHONE_NUMBERS", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.FINGERPRINT", "android.hardware.fingerprint");
        PERMISSION_TO_FEATURE.put("android.permission.USE_FINGERPRINT", "android.hardware.fingerprint");
        PERMISSION_TO_FEATURE.put("android.permission.BIND_CARRIER_SERVICES", "android.hardware.telephony");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_USES_PERMISSION);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String permission = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
        if (permission.isEmpty()) {
            return;
        }

        String impliedFeature = PERMISSION_TO_FEATURE.get(permission);
        if (impliedFeature == null) {
            return;
        }

        Document document = element.getOwnerDocument();
        NodeList features = document.getElementsByTagName(SdkConstants.TAG_USES_FEATURE);
        boolean hasOptionalFeature = false;

        for (int i = 0; i < features.getLength(); i++) {
            Element featureElement = (Element) features.item(i);
            String featureName = featureElement.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if (impliedFeature.equals(featureName)) {
                String required = featureElement.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_REQUIRED);
                if ("false".equals(required)) {
                    hasOptionalFeature = true;
                    break;
                }
            }
        }

        if (!hasOptionalFeature) {
            String message = String.format(
                "Permission `%s` implies hardware feature `%s`, which is not supported on Android TV. " +
                "Add `<uses-feature android:name=\"%s\" android:required=\"false\" />` to prevent filtering on TV devices.",
                permission, impliedFeature, impliedFeature);
            context.report(ISSUE, element, context.getLocation(element), message);
        }
    }
}
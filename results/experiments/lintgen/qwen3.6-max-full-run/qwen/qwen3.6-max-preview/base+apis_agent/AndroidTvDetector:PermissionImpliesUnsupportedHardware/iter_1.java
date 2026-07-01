package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "PermissionImpliesUnsupportedHardware",
            "Permission Implies Unsupported Hardware",
            "The `<uses-permission>` element should not require a permission that implies an unsupported TV hardware feature. " +
            "Google Play assumes that certain hardware related permissions indicate that the underlying hardware features are required by default. " +
            "To fix the issue, consider declaring the corresponding `uses-feature` element with `required=\"false\"` attribute.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private static final Map<String, String> PERMISSION_TO_FEATURE = new HashMap<>();

    static {
        PERMISSION_TO_FEATURE.put("android.permission.CAMERA", "android.hardware.camera");
        PERMISSION_TO_FEATURE.put("android.permission.RECORD_AUDIO", "android.hardware.microphone");
        PERMISSION_TO_FEATURE.put("android.permission.NFC", "android.hardware.nfc");
        PERMISSION_TO_FEATURE.put("android.permission.ACCESS_FINE_LOCATION", "android.hardware.location.gps");
        PERMISSION_TO_FEATURE.put("android.permission.ACCESS_COARSE_LOCATION", "android.hardware.location.network");
        PERMISSION_TO_FEATURE.put("android.permission.ACCESS_MOCK_LOCATION", "android.hardware.location");
        PERMISSION_TO_FEATURE.put("android.permission.ACCESS_LOCATION_EXTRA_COMMANDS", "android.hardware.location");
        PERMISSION_TO_FEATURE.put("android.permission.INSTALL_LOCATION_PROVIDER", "android.hardware.location");

        String[] telephonyPerms = {
                "android.permission.CALL_PHONE",
                "android.permission.CALL_PRIVILEGED",
                "android.permission.MODIFY_PHONE_STATE",
                "android.permission.PROCESS_OUTGOING_CALLS",
                "android.permission.READ_SMS",
                "android.permission.RECEIVE_SMS",
                "android.permission.RECEIVE_MMS",
                "android.permission.RECEIVE_WAP_PUSH",
                "android.permission.SEND_SMS",
                "android.permission.WRITE_APN_SETTINGS",
                "android.permission.WRITE_SMS"
        };
        for (String perm : telephonyPerms) {
            PERMISSION_TO_FEATURE.put(perm, "android.hardware.telephony");
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("uses-permission");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String permission = element.getAttributeNS(SdkConstants.ANDROID_URI, "name");
        if (permission == null || permission.isEmpty()) {
            return;
        }

        String impliedFeature = PERMISSION_TO_FEATURE.get(permission);
        if (impliedFeature == null) {
            return;
        }

        Document document = context.document;
        if (document == null) {
            return;
        }

        NodeList features = document.getElementsByTagName("uses-feature");
        boolean isOptional = false;
        for (int i = 0; i < features.getLength(); i++) {
            Element featureEl = (Element) features.item(i);
            String featureName = featureEl.getAttributeNS(SdkConstants.ANDROID_URI, "name");
            if (impliedFeature.equals(featureName)) {
                String required = featureEl.getAttributeNS(SdkConstants.ANDROID_URI, "required");
                if ("false".equals(required)) {
                    isOptional = true;
                    break;
                }
            }
        }

        if (!isOptional) {
            String message = String.format(
                    "Permission `%s` implies unsupported TV hardware feature `%s`. " +
                    "Declare `<uses-feature android:name=\"%s\" android:required=\"false\" />` to make it optional.",
                    permission, impliedFeature, impliedFeature);
            context.report(ISSUE, element, context.getLocation(element), message);
        }
    }
}
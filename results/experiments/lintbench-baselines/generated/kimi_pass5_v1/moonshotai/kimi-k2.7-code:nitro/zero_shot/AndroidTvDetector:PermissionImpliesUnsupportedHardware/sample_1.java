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
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, EnumSet.of(Scope.MANIFEST_SCOPE));

    public static final Issue ISSUE_PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE =
            Issue.create(
                    "PermissionImpliesUnsupportedHardware",
                    "Permission Implies Unsupported Hardware",
                    "The `<uses-permission>` element should not require a permission that implies "
                            + "an unsupported TV hardware feature. Google Play assumes that certain "
                            + "hardware related permissions indicate that the underlying hardware "
                            + "features are required by default. To fix the issue, consider declaring "
                            + "the corresponding `uses-feature` element with `required=\"false\"` "
                            + "attribute.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION)
                    .setMoreInfo(
                            "https://developer.android.com/guide/topics/manifest/uses-feature-element.html#permissions");

    private static final Map<String, String> PERMISSION_TO_FEATURE;

    static {
        Map<String, String> map = new HashMap<>();
        map.put("android.permission.CAMERA", "android.hardware.camera");
        map.put("android.permission.RECORD_AUDIO", "android.hardware.microphone");
        map.put("android.permission.ACCESS_FINE_LOCATION", "android.hardware.location.gps");
        map.put("android.permission.ACCESS_COARSE_LOCATION", "android.hardware.location");
        map.put("android.permission.CALL_PHONE", "android.hardware.telephony");
        map.put("android.permission.CALL_PRIVILEGED", "android.hardware.telephony");
        map.put("android.permission.MODIFY_PHONE_STATE", "android.hardware.telephony");
        map.put("android.permission.PROCESS_OUTGOING_CALLS", "android.hardware.telephony");
        map.put("android.permission.READ_PHONE_STATE", "android.hardware.telephony");
        map.put("android.permission.READ_SMS", "android.hardware.telephony");
        map.put("android.permission.RECEIVE_SMS", "android.hardware.telephony");
        map.put("android.permission.RECEIVE_MMS", "android.hardware.telephony");
        map.put("android.permission.RECEIVE_WAP_PUSH", "android.hardware.telephony");
        map.put("android.permission.SEND_SMS", "android.hardware.telephony");
        map.put("android.permission.WRITE_APN_SETTINGS", "android.hardware.telephony");
        map.put("android.permission.WRITE_SMS", "android.hardware.telephony");
        PERMISSION_TO_FEATURE = Collections.unmodifiableMap(map);
    }

    @Override
    public void visitDocument(@NotNull XmlContext context, @NotNull Document document) {
        Map<String, Boolean> features = new HashMap<>();

        NodeList featureNodes = document.getElementsByTagName(SdkConstants.TAG_USES_FEATURE);
        for (int i = 0; i < featureNodes.getLength(); i++) {
            Element element = (Element) featureNodes.item(i);
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if (name.isEmpty()) {
                continue;
            }

            String required =
                    element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_REQUIRED);
            if ("false".equals(required)) {
                features.put(name, Boolean.FALSE);
            } else if (!features.containsKey(name)) {
                features.put(name, Boolean.TRUE);
            }
        }

        NodeList permissionNodes = document.getElementsByTagName(SdkConstants.TAG_USES_PERMISSION);
        for (int i = 0; i < permissionNodes.getLength(); i++) {
            Element element = (Element) permissionNodes.item(i);
            String permission =
                    element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            String feature = PERMISSION_TO_FEATURE.get(permission);
            if (feature == null) {
                continue;
            }

            Boolean required = features.get(feature);
            if (required == null || required) {
                context.report(
                        ISSUE_PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE,
                        element,
                        context.getLocation(element),
                        String.format(
                                "Permission `%1$s` implies `%2$s`, a hardware feature not supported "
                                        + "on Android TV; declare `<uses-feature android:name=\"%2$s\" "
                                        + "android:required=\"false\" />`",
                                permission,
                                feature));
            }
        }
    }
}
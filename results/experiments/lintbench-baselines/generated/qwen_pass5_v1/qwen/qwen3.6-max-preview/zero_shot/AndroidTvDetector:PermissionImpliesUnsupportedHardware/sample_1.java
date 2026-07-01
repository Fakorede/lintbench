package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.jetbrains.annotations.NonNull;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final Map<String, String> PERMISSION_TO_FEATURE = new HashMap<>();
    static {
        PERMISSION_TO_FEATURE.put("android.permission.CAMERA", "android.hardware.camera");
        PERMISSION_TO_FEATURE.put("android.permission.ACCESS_FINE_LOCATION", "android.hardware.location.gps");
        PERMISSION_TO_FEATURE.put("android.permission.ACCESS_COARSE_LOCATION", "android.hardware.location.network");
        PERMISSION_TO_FEATURE.put("android.permission.RECORD_AUDIO", "android.hardware.microphone");
        PERMISSION_TO_FEATURE.put("android.permission.NFC", "android.hardware.nfc");

        String[] telephonyPerms = {
            "android.permission.CALL_PHONE", "android.permission.CALL_PRIVILEGED",
            "android.permission.MODIFY_PHONE_STATE", "android.permission.PROCESS_OUTGOING_CALLS",
            "android.permission.READ_PHONE_STATE", "android.permission.READ_PHONE_NUMBERS",
            "android.permission.SEND_SMS", "android.permission.RECEIVE_SMS",
            "android.permission.READ_SMS", "android.permission.WRITE_SMS",
            "android.permission.RECEIVE_MMS", "android.permission.RECEIVE_WAP_PUSH",
            "android.permission.ADD_VOICEMAIL", "android.permission.USE_SIP",
            "android.permission.ANSWER_PHONE_CALLS"
        };
        for (String perm : telephonyPerms) {
            PERMISSION_TO_FEATURE.put(perm, "android.hardware.telephony");
        }
    }

    public static final Issue ISSUE = Issue.create(
        "PermissionImpliesUnsupportedHardware",
        "Permission Implies Unsupported Hardware",
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
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String permission = element.getAttributeNS(ANDROID_URI, "name");
        if (permission.isEmpty()) {
            return;
        }

        String feature = PERMISSION_TO_FEATURE.get(permission);
        if (feature == null) {
            return;
        }

        Element manifest = element.getOwnerDocument().getDocumentElement();
        if (manifest == null || hasOptionalFeature(manifest, feature)) {
            return;
        }

        String message = String.format(
            "Permission `%1$s` implies unsupported TV hardware feature `%2$s`. " +
            "Google Play assumes this hardware is required. Add `<uses-feature android:name=\"%2$s\" android:required=\"false\" />` to support Android TV.",
            permission, feature);

        context.report(ISSUE, element, context.getLocation(element), message);
    }

    private static boolean hasOptionalFeature(@NonNull Element manifest, @NonNull String featureName) {
        NodeList list = manifest.getElementsByTagName("uses-feature");
        for (int i = 0, n = list.getLength(); i < n; i++) {
            Element feature = (Element) list.item(i);
            String name = feature.getAttributeNS(ANDROID_URI, "name");
            if (featureName.equals(name)) {
                String required = feature.getAttributeNS(ANDROID_URI, "required");
                return "false".equals(required);
            }
        }
        return false;
    }
}
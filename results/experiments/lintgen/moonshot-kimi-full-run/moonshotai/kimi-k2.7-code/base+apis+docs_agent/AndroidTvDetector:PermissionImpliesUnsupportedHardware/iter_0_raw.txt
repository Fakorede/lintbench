package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_REQUIRED;
import static com.android.SdkConstants.TAG_USES_FEATURE;
import static com.android.SdkConstants.TAG_USES_PERMISSION;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final Map<String, String> PERMISSION_TO_FEATURE = new HashMap<>();

    static {
        PERMISSION_TO_FEATURE.put("android.permission.CAMERA", "android.hardware.camera");
        PERMISSION_TO_FEATURE.put("android.permission.FLASHLIGHT", "android.hardware.camera.flash");
        PERMISSION_TO_FEATURE.put("android.permission.RECORD_AUDIO", "android.hardware.microphone");
        PERMISSION_TO_FEATURE.put("android.permission.ACCESS_FINE_LOCATION", "android.hardware.location.gps");
        PERMISSION_TO_FEATURE.put("android.permission.ACCESS_COARSE_LOCATION", "android.hardware.location.network");
        PERMISSION_TO_FEATURE.put("android.permission.CALL_PHONE", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.CALL_PRIVILEGED", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.PROCESS_OUTGOING_CALLS", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.MODIFY_PHONE_STATE", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.READ_PHONE_STATE", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.READ_SMS", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.RECEIVE_SMS", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.SEND_SMS", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.NFC", "android.hardware.nfc");
    }

    private static final Implementation IMPLEMENTATION = new Implementation(
            AndroidTvDetector.class,
            Scope.MANIFEST_SCOPE
    );

    public static final Issue ISSUE_PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE = Issue.create(
            "PermissionImpliesUnsupportedHardware",
            "Permission implies unsupported TV hardware",
            "The `<uses-permission>` element should not require a permission that implies an unsupported TV hardware feature. "
                    + "Google Play assumes that certain hardware-related permissions indicate that the underlying hardware "
                    + "features are required by default. To fix the issue, declare the corresponding `<uses-feature>` element "
                    + "with `android:required=\"false\"`.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            IMPLEMENTATION
    );

    @Override
    @NonNull
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES_PERMISSION);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String permission = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (permission == null || permission.isEmpty()) {
            return;
        }

        String feature = PERMISSION_TO_FEATURE.get(permission);
        if (feature == null) {
            return;
        }

        if (!hasRequiredFalseFeature(context, feature)) {
            String message = String.format(
                    "Permission `%1$s` implies feature `%2$s`, which is not supported on Android TV. "
                            + "Consider adding `<uses-feature android:name=\"%2$s\" android:required=\"false\" />`.",
                    permission,
                    feature
            );
            context.report(ISSUE_PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE, element,
                    context.getValueLocation(ATTR_NAME), message);
        }
    }

    private static boolean hasRequiredFalseFeature(@NonNull XmlContext context, @NonNull String feature) {
        Element root = context.document.getDocumentElement();
        if (root == null) {
            return false;
        }

        NodeList features = root.getElementsByTagName(TAG_USES_FEATURE);
        for (int i = 0, n = features.getLength(); i < n; i++) {
            Element element = (Element) features.item(i);
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (feature.equals(name)) {
                String required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
                return "false".equals(required);
            }
        }

        return false;
    }
}
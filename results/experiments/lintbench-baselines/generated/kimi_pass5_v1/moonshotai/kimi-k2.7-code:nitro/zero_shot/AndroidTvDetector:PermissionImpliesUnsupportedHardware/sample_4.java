package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    private static final String HARDWARE_FEATURE_CAMERA = "android.hardware.camera";
    private static final String HARDWARE_FEATURE_MICROPHONE = "android.hardware.microphone";
    private static final String HARDWARE_FEATURE_LOCATION_GPS = "android.hardware.location.gps";
    private static final String HARDWARE_FEATURE_LOCATION_NETWORK = "android.hardware.location.network";
    private static final String HARDWARE_FEATURE_TELEPHONY = "android.hardware.telephony";

    private static final Map<String, String> PERMISSION_TO_FEATURE;
    static {
        Map<String, String> map = new HashMap<>();
        map.put("android.permission.CAMERA", HARDWARE_FEATURE_CAMERA);
        map.put("android.permission.RECORD_AUDIO", HARDWARE_FEATURE_MICROPHONE);
        map.put("android.permission.ACCESS_FINE_LOCATION", HARDWARE_FEATURE_LOCATION_GPS);
        map.put("android.permission.ACCESS_COARSE_LOCATION", HARDWARE_FEATURE_LOCATION_NETWORK);
        map.put("android.permission.CALL_PHONE", HARDWARE_FEATURE_TELEPHONY);
        map.put("android.permission.CALL_PRIVILEGED", HARDWARE_FEATURE_TELEPHONY);
        map.put("android.permission.MODIFY_PHONE_STATE", HARDWARE_FEATURE_TELEPHONY);
        map.put("android.permission.PROCESS_OUTGOING_CALLS", HARDWARE_FEATURE_TELEPHONY);
        map.put("android.permission.READ_SMS", HARDWARE_FEATURE_TELEPHONY);
        map.put("android.permission.RECEIVE_SMS", HARDWARE_FEATURE_TELEPHONY);
        map.put("android.permission.RECEIVE_MMS", HARDWARE_FEATURE_TELEPHONY);
        map.put("android.permission.RECEIVE_WAP_PUSH", HARDWARE_FEATURE_TELEPHONY);
        map.put("android.permission.SEND_SMS", HARDWARE_FEATURE_TELEPHONY);
        map.put("android.permission.WRITE_SMS", HARDWARE_FEATURE_TELEPHONY);
        map.put("android.permission.READ_PHONE_STATE", HARDWARE_FEATURE_TELEPHONY);
        PERMISSION_TO_FEATURE = Collections.unmodifiableMap(map);
    }

    public static final Issue PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE = Issue.create(
            "PermissionImpliesUnsupportedHardware",
            "Permission implies unsupported TV hardware",
            "The `<uses-permission>` element should not require a permission that implies an "
                    + "unsupported TV hardware feature. Google Play assumes that certain "
                    + "hardware-related permissions indicate that the underlying hardware "
                    + "features are required by default. To fix the issue, consider declaring "
                    + "the corresponding `<uses-feature>` element with `required=\"false\"`.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation<>(AndroidTvDetector.class, Scope.MANIFEST_SCOPE)
    );

    @Override
    @NonNull
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_USES_PERMISSION);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String permission = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
        if (permission.isEmpty()) {
            return;
        }

        String feature = PERMISSION_TO_FEATURE.get(permission);
        if (feature == null) {
            return;
        }

        if (hasRequiredFalseFeature(context.document, feature)) {
            return;
        }

        String message = String.format(
                "Permission `%1$s` implies feature `%2$s`, which is not supported on Android TV. "
                        + "Consider adding `<uses-feature android:name=\"%2$s\" "
                        + "android:required=\"false\" />`.",
                permission, feature);
        context.report(PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE, context.getNameLocation(element), message);
    }

    private static boolean hasRequiredFalseFeature(@NonNull Document document, @NonNull String feature) {
        NodeList features = document.getElementsByTagName(SdkConstants.TAG_USES_FEATURE);
        for (int i = 0; i < features.getLength(); i++) {
            Node node = features.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) node;
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if (!feature.equals(name)) {
                continue;
            }
            String required = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_REQUIRED);
            if ("false".equals(required)) {
                return true;
            }
        }
        return false;
    }
}
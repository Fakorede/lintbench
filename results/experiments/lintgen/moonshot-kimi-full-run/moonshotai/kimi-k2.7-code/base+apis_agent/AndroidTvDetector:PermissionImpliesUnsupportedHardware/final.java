package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final Map<String, List<String>> PERMISSION_TO_FEATURES;
    static {
        Map<String, List<String>> map = new HashMap<>();
        map.put("android.permission.CAMERA",
                Arrays.asList("android.hardware.camera", "android.hardware.camera.autofocus"));
        map.put("android.permission.FLASHLIGHT",
                Collections.singletonList("android.hardware.camera.flash"));
        map.put("android.permission.RECORD_AUDIO",
                Collections.singletonList("android.hardware.microphone"));
        map.put("android.permission.ACCESS_FINE_LOCATION",
                Arrays.asList("android.hardware.location.gps", "android.hardware.location"));
        map.put("android.permission.ACCESS_COARSE_LOCATION",
                Arrays.asList("android.hardware.location.network", "android.hardware.location"));
        map.put("android.permission.NFC",
                Collections.singletonList("android.hardware.nfc"));
        map.put("android.permission.CALL_PHONE",
                Collections.singletonList("android.hardware.telephony"));
        map.put("android.permission.READ_PHONE_STATE",
                Collections.singletonList("android.hardware.telephony"));
        map.put("android.permission.SEND_SMS",
                Collections.singletonList("android.hardware.telephony"));
        map.put("android.permission.BODY_SENSORS",
                Collections.singletonList("android.hardware.sensor"));
        map.put("android.permission.USE_FINGERPRINT",
                Collections.singletonList("android.hardware.fingerprint"));
        map.put("android.permission.USE_BIOMETRIC",
                Collections.singletonList("android.hardware.fingerprint"));
        PERMISSION_TO_FEATURES = Collections.unmodifiableMap(map);
    }

    public static final Issue ISSUE = Issue.create(
            "PermissionImpliesUnsupportedHardware",
            "Permission implies unsupported TV hardware",
            "The `<uses-permission>` element should not require a permission that implies "
                    + "a hardware feature that is unsupported on Android TV. Google Play assumes "
                    + "that certain permissions indicate that the underlying hardware features are "
                    + "required by default. To avoid being filtered out on TV devices that lack "
                    + "the hardware, declare the corresponding `<uses-feature>` element with "
                    + "`android:required=\"false\"`.",
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
        String permission = element.getAttributeNS(ANDROID_URI, "name");
        if (permission == null || permission.isEmpty()) {
            return;
        }

        List<String> impliedFeatures = PERMISSION_TO_FEATURES.get(permission);
        if (impliedFeatures == null) {
            return;
        }

        Document document = context.document;
        for (String feature : impliedFeatures) {
            if (!isFeatureDeclaredNotRequired(document, feature)) {
                String message = String.format(
                        "Permission `%1$s` implies unsupported TV hardware feature `%2$s`; "
                                + "consider adding `<uses-feature android:name=\"%2$s\" "
                                + "android:required=\"false\" />`.",
                        permission, feature);
                context.report(ISSUE, element, context.getLocation(element), message);
            }
        }
    }

    private static boolean isFeatureDeclaredNotRequired(Document document, String featureName) {
        NodeList features = document.getElementsByTagName("uses-feature");
        for (int i = 0; i < features.getLength(); i++) {
            Element feature = (Element) features.item(i);
            if (featureName.equals(feature.getAttributeNS(ANDROID_URI, "name"))) {
                String required = feature.getAttributeNS(ANDROID_URI, "required");
                if ("false".equals(required)) {
                    return true;
                }
            }
        }
        return false;
    }
}
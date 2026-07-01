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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    private static final String PERMISSION_PREFIX = "android.permission.";

    private static final Map<String, List<String>> PERMISSION_TO_FEATURES;
    static {
        Map<String, List<String>> map = new HashMap<>();
        map.put("android.permission.CAMERA",
                Arrays.asList("android.hardware.camera", "android.hardware.camera.autofocus"));
        map.put("android.permission.ACCESS_FINE_LOCATION",
                Arrays.asList("android.hardware.location", "android.hardware.location.gps"));
        map.put("android.permission.ACCESS_COARSE_LOCATION",
                Arrays.asList("android.hardware.location", "android.hardware.location.network"));
        map.put("android.permission.CALL_PHONE",
                Collections.singletonList("android.hardware.telephony"));
        map.put("android.permission.READ_PHONE_STATE",
                Collections.singletonList("android.hardware.telephony"));
        map.put("android.permission.SEND_SMS",
                Collections.singletonList("android.hardware.telephony"));
        map.put("android.permission.RECEIVE_SMS",
                Collections.singletonList("android.hardware.telephony"));
        map.put("android.permission.READ_SMS",
                Collections.singletonList("android.hardware.telephony"));
        map.put("android.permission.RECEIVE_WAP_PUSH",
                Collections.singletonList("android.hardware.telephony"));
        map.put("android.permission.RECEIVE_MMS",
                Collections.singletonList("android.hardware.telephony"));
        map.put("android.permission.BODY_SENSORS",
                Collections.singletonList("android.hardware.sensor.heartrate"));
        PERMISSION_TO_FEATURES = Collections.unmodifiableMap(map);
    }

    public static final Issue ISSUE = Issue.create(
            "PermissionImpliesUnsupportedHardware",
            "Permission implies unsupported TV hardware feature",
            "The `<uses-permission>` element should not require a permission that implies "
                    + "an unsupported TV hardware feature. Google Play assumes that certain "
                    + "hardware related permissions indicate that the underlying hardware "
                    + "features are required by default. To fix the issue, consider declaring "
                    + "the corresponding `<uses-feature>` element with "
                    + "`android:required=\"false\"`.",
            Category.TV,
            5,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

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

        String normalized = permission;
        if (!normalized.startsWith(PERMISSION_PREFIX)) {
            normalized = PERMISSION_PREFIX + normalized;
        }

        List<String> impliedFeatures = PERMISSION_TO_FEATURES.get(normalized);
        if (impliedFeatures == null) {
            return;
        }

        Element root = context.document.getDocumentElement();
        for (String feature : impliedFeatures) {
            if (!hasOptionalFeature(root, feature)) {
                String message = String.format(
                        "Permission `%1$s` implies feature `%2$s`, which is not supported on "
                                + "Android TV. Add `<uses-feature android:name=\"%2$s\" "
                                + "android:required=\"false\" /> to avoid requiring it.",
                        permission, feature);
                context.report(ISSUE, element, context.getLocation(element), message);
            }
        }
    }

    private static boolean hasOptionalFeature(@NonNull Element root, @NonNull String feature) {
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
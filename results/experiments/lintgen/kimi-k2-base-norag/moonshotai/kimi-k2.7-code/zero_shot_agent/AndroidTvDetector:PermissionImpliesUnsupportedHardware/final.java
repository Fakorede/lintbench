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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    private static final String PERMISSION_CAMERA = "android.permission.CAMERA";
    private static final String PERMISSION_RECORD_AUDIO = "android.permission.RECORD_AUDIO";
    private static final String PERMISSION_ACCESS_FINE_LOCATION =
            "android.permission.ACCESS_FINE_LOCATION";
    private static final String PERMISSION_ACCESS_COARSE_LOCATION =
            "android.permission.ACCESS_COARSE_LOCATION";
    private static final String PERMISSION_CALL_PHONE = "android.permission.CALL_PHONE";
    private static final String PERMISSION_READ_PHONE_STATE =
            "android.permission.READ_PHONE_STATE";
    private static final String PERMISSION_SEND_SMS = "android.permission.SEND_SMS";
    private static final String PERMISSION_RECEIVE_SMS = "android.permission.RECEIVE_SMS";
    private static final String PERMISSION_READ_SMS = "android.permission.READ_SMS";
    private static final String PERMISSION_RECEIVE_WAP_PUSH =
            "android.permission.RECEIVE_WAP_PUSH";
    private static final String PERMISSION_RECEIVE_MMS = "android.permission.RECEIVE_MMS";
    private static final String PERMISSION_BODY_SENSORS = "android.permission.BODY_SENSORS";

    private static final String FEATURE_CAMERA = "android.hardware.camera";
    private static final String FEATURE_CAMERA_AUTOFOCUS = "android.hardware.camera.autofocus";
    private static final String FEATURE_MICROPHONE = "android.hardware.microphone";
    private static final String FEATURE_LOCATION = "android.hardware.location";
    private static final String FEATURE_LOCATION_GPS = "android.hardware.location.gps";
    private static final String FEATURE_LOCATION_NETWORK = "android.hardware.location.network";
    private static final String FEATURE_TELEPHONY = "android.hardware.telephony";
    private static final String FEATURE_SENSOR_HEART_RATE = "android.hardware.sensor.heart_rate";

    private static final Map<String, List<String>> IMPLIED_FEATURES;
    static {
        Map<String, List<String>> map = new HashMap<>();
        map.put(PERMISSION_CAMERA,
                Arrays.asList(FEATURE_CAMERA, FEATURE_CAMERA_AUTOFOCUS));
        map.put(PERMISSION_RECORD_AUDIO,
                Collections.singletonList(FEATURE_MICROPHONE));
        map.put(PERMISSION_ACCESS_FINE_LOCATION,
                Arrays.asList(FEATURE_LOCATION, FEATURE_LOCATION_GPS));
        map.put(PERMISSION_ACCESS_COARSE_LOCATION,
                Arrays.asList(FEATURE_LOCATION, FEATURE_LOCATION_NETWORK));
        map.put(PERMISSION_CALL_PHONE,
                Collections.singletonList(FEATURE_TELEPHONY));
        map.put(PERMISSION_READ_PHONE_STATE,
                Collections.singletonList(FEATURE_TELEPHONY));
        map.put(PERMISSION_SEND_SMS,
                Collections.singletonList(FEATURE_TELEPHONY));
        map.put(PERMISSION_RECEIVE_SMS,
                Collections.singletonList(FEATURE_TELEPHONY));
        map.put(PERMISSION_READ_SMS,
                Collections.singletonList(FEATURE_TELEPHONY));
        map.put(PERMISSION_RECEIVE_WAP_PUSH,
                Collections.singletonList(FEATURE_TELEPHONY));
        map.put(PERMISSION_RECEIVE_MMS,
                Collections.singletonList(FEATURE_TELEPHONY));
        map.put(PERMISSION_BODY_SENSORS,
                Collections.singletonList(FEATURE_SENSOR_HEART_RATE));
        IMPLIED_FEATURES = Collections.unmodifiableMap(map);
    }

    public static final Issue ISSUE = Issue.create(
            "PermissionImpliesUnsupportedHardware",
            "Permission implies unsupported TV hardware",
            "The `<uses-permission>` element should not require a permission that implies "
                    + "an unsupported TV hardware feature. Google Play assumes that certain "
                    + "hardware related permissions indicate that the underlying hardware "
                    + "features are required by default. To fix the issue, consider declaring "
                    + "the corresponding `<uses-feature>` element with "
                    + "`android:required=\"false\"`.",
            Category.CORRECTNESS,
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

        List<String> impliedFeatures = IMPLIED_FEATURES.get(permission);
        if (impliedFeatures == null || impliedFeatures.isEmpty()) {
            return;
        }

        Map<String, Boolean> declaredFeatures = new HashMap<>();
        NodeList featureNodes = element.getOwnerDocument().getElementsByTagName(TAG_USES_FEATURE);
        for (int i = 0; i < featureNodes.getLength(); i++) {
            Element feature = (Element) featureNodes.item(i);
            String name = feature.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (name == null || name.isEmpty()) {
                continue;
            }
            String required = feature.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
            declaredFeatures.put(name, "false".equals(required));
        }

        List<String> missingFeatures = new ArrayList<>();
        for (String feature : impliedFeatures) {
            Boolean requiredFalse = declaredFeatures.get(feature);
            if (requiredFalse == null || !requiredFalse) {
                missingFeatures.add(feature);
            }
        }

        if (!missingFeatures.isEmpty()) {
            StringBuilder message = new StringBuilder();
            message.append("Permission `")
                    .append(permission)
                    .append("` implies hardware feature");
            if (missingFeatures.size() > 1) {
                message.append("s");
            }
            message.append(" ");
            for (int i = 0; i < missingFeatures.size(); i++) {
                if (i > 0) {
                    message.append(", ");
                }
                message.append("`").append(missingFeatures.get(i)).append("`");
            }
            message.append(" which ");
            if (missingFeatures.size() > 1) {
                message.append("are");
            } else {
                message.append("is");
            }
            message.append(" not supported on TVs. Consider adding the corresponding "
                    + "`<uses-feature android:required=\"false\" />` element(s).");
            context.report(ISSUE, element, context.getLocation(element), message.toString());
        }
    }
}
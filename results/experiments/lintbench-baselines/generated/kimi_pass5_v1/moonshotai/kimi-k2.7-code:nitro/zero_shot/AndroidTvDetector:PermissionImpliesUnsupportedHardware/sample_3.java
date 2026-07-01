package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "PermissionImpliesUnsupportedHardware",
                    "Permission Implies Unsupported TV Hardware",
                    "The `<uses-permission>` element should not require a permission that "
                            + "implies an unsupported TV hardware feature. Google Play assumes "
                            + "that certain hardware related permissions indicate that the "
                            + "underlying hardware features are required by default. To fix "
                            + "the issue, consider declaring the corresponding `uses-feature` "
                            + "element with `required=\"false\"` attribute.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String TAG_USES_PERMISSION = "uses-permission";
    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_REQUIRED = "required";

    private static final String FEATURE_CAMERA = "android.hardware.camera";
    private static final String FEATURE_CAMERA_AUTOFOCUS = "android.hardware.camera.autofocus";
    private static final String FEATURE_MICROPHONE = "android.hardware.microphone";
    private static final String FEATURE_LOCATION_GPS = "android.hardware.location.gps";
    private static final String FEATURE_LOCATION_NETWORK = "android.hardware.location.network";
    private static final String FEATURE_NFC = "android.hardware.nfc";
    private static final String FEATURE_TELEPHONY = "android.hardware.telephony";

    private static final Map<String, List<String>> PERMISSION_TO_FEATURES = new HashMap<>();

    static {
        PERMISSION_TO_FEATURES.put(
                "android.permission.CAMERA",
                Arrays.asList(FEATURE_CAMERA, FEATURE_CAMERA_AUTOFOCUS));
        PERMISSION_TO_FEATURES.put(
                "android.permission.RECORD_AUDIO",
                Arrays.asList(FEATURE_MICROPHONE));
        PERMISSION_TO_FEATURES.put(
                "android.permission.ACCESS_FINE_LOCATION",
                Arrays.asList(FEATURE_LOCATION_GPS));
        PERMISSION_TO_FEATURES.put(
                "android.permission.ACCESS_COARSE_LOCATION",
                Arrays.asList(FEATURE_LOCATION_NETWORK));
        PERMISSION_TO_FEATURES.put(
                "android.permission.NFC",
                Arrays.asList(FEATURE_NFC));

        List<String> telephonyFeatures = Arrays.asList(FEATURE_TELEPHONY);
        PERMISSION_TO_FEATURES.put("android.permission.CALL_PHONE", telephonyFeatures);
        PERMISSION_TO_FEATURES.put("android.permission.CALL_PRIVILEGED", telephonyFeatures);
        PERMISSION_TO_FEATURES.put("android.permission.MODIFY_PHONE_STATE", telephonyFeatures);
        PERMISSION_TO_FEATURES.put("android.permission.PROCESS_OUTGOING_CALLS", telephonyFeatures);
        PERMISSION_TO_FEATURES.put("android.permission.READ_PHONE_STATE", telephonyFeatures);
        PERMISSION_TO_FEATURES.put("android.permission.READ_SMS", telephonyFeatures);
        PERMISSION_TO_FEATURES.put("android.permission.RECEIVE_SMS", telephonyFeatures);
        PERMISSION_TO_FEATURES.put("android.permission.SEND_SMS", telephonyFeatures);
        PERMISSION_TO_FEATURES.put("android.permission.WRITE_SMS", telephonyFeatures);
        PERMISSION_TO_FEATURES.put("android.permission.BROADCAST_SMS", telephonyFeatures);
        PERMISSION_TO_FEATURES.put("android.permission.BROADCAST_WAP_PUSH", telephonyFeatures);
    }

    @Override
    @NotNull
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_USES_PERMISSION);
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        String permission = element.getAttributeNS(ANDROID_NS, ATTR_NAME);
        if (permission == null || permission.isEmpty()) {
            return;
        }

        List<String> features = PERMISSION_TO_FEATURES.get(permission);
        if (features == null) {
            return;
        }

        Document document = element.getOwnerDocument();
        Location location = context.getElementLocation(element);

        for (String feature : features) {
            if (!hasRequiredFalseFeature(document, feature)) {
                String message =
                        "Permission `"
                                + permission
                                + "` implies `"
                                + feature
                                + "`, which is not supported on Android TV. "
                                + "Consider adding `<uses-feature android:name=\""
                                + feature
                                + "\" android:required=\"false\" />`.";
                context.report(ISSUE, element, location, message);
            }
        }
    }

    private static boolean hasRequiredFalseFeature(@NotNull Document document, @NotNull String feature) {
        NodeList features = document.getElementsByTagName(TAG_USES_FEATURE);
        for (int i = 0; i < features.getLength(); i++) {
            Element element = (Element) features.item(i);
            String name = element.getAttributeNS(ANDROID_NS, ATTR_NAME);
            if (feature.equals(name)) {
                String required = element.getAttributeNS(ANDROID_NS, ATTR_REQUIRED);
                return "false".equals(required);
            }
        }
        return false;
    }
}
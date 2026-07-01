package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_REQUIRED;
import static com.android.SdkConstants.TAG_USES_FEATURE;
import static com.android.SdkConstants.TAG_USES_PERMISSION;
import static com.android.SdkConstants.VALUE_FALSE;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "PermissionImpliesUnsupportedHardware",
                    "Permission Implies Unsupported Hardware",
                    "The `<uses-permission>` element should not require a permission that implies an "
                            + "unsupported TV hardware feature. Google Play assumes that certain hardware "
                            + "related permissions indicate that the underlying hardware features are "
                            + "required by default. To fix the issue, consider declaring the corresponding "
                            + "`uses-feature` element with `required=\"false\"` attribute.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private static final Map<String, String> PERMISSION_TO_FEATURE = new HashMap<>();
    static {
        PERMISSION_TO_FEATURE.put("android.permission.CAMERA", "android.hardware.camera");
        PERMISSION_TO_FEATURE.put("android.permission.ACCESS_FINE_LOCATION", "android.hardware.location.gps");
        PERMISSION_TO_FEATURE.put("android.permission.ACCESS_COARSE_LOCATION", "android.hardware.location.network");
        PERMISSION_TO_FEATURE.put("android.permission.BLUETOOTH", "android.hardware.bluetooth");
        PERMISSION_TO_FEATURE.put("android.permission.BLUETOOTH_ADMIN", "android.hardware.bluetooth");
        PERMISSION_TO_FEATURE.put("android.permission.NFC", "android.hardware.nfc");
        PERMISSION_TO_FEATURE.put("android.permission.CALL_PHONE", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.SEND_SMS", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.RECEIVE_SMS", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.READ_PHONE_STATE", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.ACCESS_WIFI_STATE", "android.hardware.wifi");
        PERMISSION_TO_FEATURE.put("android.permission.CHANGE_WIFI_STATE", "android.hardware.wifi");
    }

    private Set<String> optionalFeatures;

    @NonNull
    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_USES_PERMISSION);
    }

    @Override
    public void beforeCheckFile(@NonNull XmlContext context) {
        optionalFeatures = new HashSet<>();
        if (context.document != null) {
            NodeList features = context.document.getElementsByTagName(TAG_USES_FEATURE);
            for (int i = 0; i < features.getLength(); i++) {
                Element feature = (Element) features.item(i);
                String required = feature.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
                if (VALUE_FALSE.equals(required)) {
                    String name = feature.getAttributeNS(ANDROID_URI, ATTR_NAME);
                    if (name != null && !name.isEmpty()) {
                        optionalFeatures.add(name);
                    }
                }
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String permission = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (permission == null || permission.isEmpty()) {
            return;
        }

        String impliedFeature = PERMISSION_TO_FEATURE.get(permission);
        if (impliedFeature != null && !optionalFeatures.contains(impliedFeature)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Permission `" + permission + "` implies hardware feature `" + impliedFeature
                            + "`, which is not supported on all TV devices. Declare `<uses-feature android:name=\""
                            + impliedFeature + "\" android:required=\"false\" />` to make it optional.");
        }
    }

    @Override
    public void afterCheckFile(@NonNull XmlContext context) {
        optionalFeatures = null;
    }
}
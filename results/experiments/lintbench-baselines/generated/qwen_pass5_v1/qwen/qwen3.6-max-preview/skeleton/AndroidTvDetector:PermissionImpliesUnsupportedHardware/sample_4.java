package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "PermissionImpliesUnsupportedHardware",
                    "Permission Implies Unsupported Hardware",
                    "The `<uses-permission>` element should not require a permission that implies an unsupported TV hardware feature. "
                    + "Google Play assumes that certain hardware related permissions indicate that the underlying hardware features are required by default. "
                    + "To fix the issue, consider declaring the corresponding `<uses-feature>` element with `required=\"false\"` attribute.",
                    Category.CORRECTNESS,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final Map<String, String> PERMISSION_TO_FEATURE = new HashMap<>();
    static {
        PERMISSION_TO_FEATURE.put("android.permission.CAMERA", "android.hardware.camera");
        PERMISSION_TO_FEATURE.put("android.permission.ACCESS_COARSE_LOCATION", "android.hardware.location.network");
        PERMISSION_TO_FEATURE.put("android.permission.ACCESS_FINE_LOCATION", "android.hardware.location.gps");
        PERMISSION_TO_FEATURE.put("android.permission.ACCESS_LOCATION_EXTRA_COMMANDS", "android.hardware.location");
        PERMISSION_TO_FEATURE.put("android.permission.ACCESS_MOCK_LOCATION", "android.hardware.location");
        PERMISSION_TO_FEATURE.put("android.permission.ACCESS_WIFI_STATE", "android.hardware.wifi");
        PERMISSION_TO_FEATURE.put("android.permission.CHANGE_WIFI_STATE", "android.hardware.wifi");
        PERMISSION_TO_FEATURE.put("android.permission.CHANGE_WIFI_MULTICAST_STATE", "android.hardware.wifi");
        PERMISSION_TO_FEATURE.put("android.permission.BLUETOOTH", "android.hardware.bluetooth");
        PERMISSION_TO_FEATURE.put("android.permission.BLUETOOTH_ADMIN", "android.hardware.bluetooth");
        PERMISSION_TO_FEATURE.put("android.permission.NFC", "android.hardware.nfc");
        PERMISSION_TO_FEATURE.put("android.permission.CALL_PHONE", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.CALL_PRIVILEGED", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.MODIFY_PHONE_STATE", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.PROCESS_OUTGOING_CALLS", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.READ_SMS", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.RECEIVE_SMS", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.RECEIVE_MMS", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.RECEIVE_WAP_PUSH", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.SEND_SMS", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.WRITE_APN_SETTINGS", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.WRITE_SMS", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.RECORD_AUDIO", "android.hardware.microphone");
    }

    private List<Element> permissionElements;
    private Set<String> optionalFeatures;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("uses-permission", "uses-feature");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        permissionElements = new ArrayList<>();
        optionalFeatures = new HashSet<>();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if ("uses-permission".equals(tag)) {
            permissionElements.add(element);
        } else if ("uses-feature".equals(tag)) {
            String required = element.getAttributeNS(ANDROID_URI, "required");
            if ("false".equals(required)) {
                String name = element.getAttributeNS(ANDROID_URI, "name");
                if (name != null && !name.isEmpty()) {
                    optionalFeatures.add(name);
                }
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        for (Element permElement : permissionElements) {
            String permName = permElement.getAttributeNS(ANDROID_URI, "name");
            if (permName == null || permName.isEmpty()) {
                continue;
            }
            String impliedFeature = PERMISSION_TO_FEATURE.get(permName);
            if (impliedFeature != null && !optionalFeatures.contains(impliedFeature)) {
                String message = String.format(
                        "Permission %s implies %s hardware feature. "
                        + "Consider declaring <uses-feature android:name=\"%s\" android:required=\"false\" /> for Android TV compatibility.",
                        permName, impliedFeature, impliedFeature);
                context.report(ISSUE, context.getLocation(permElement), message);
            }
        }
    }
}
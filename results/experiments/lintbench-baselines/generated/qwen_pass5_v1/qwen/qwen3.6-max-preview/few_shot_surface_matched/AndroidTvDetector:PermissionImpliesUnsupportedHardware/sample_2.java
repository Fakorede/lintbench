package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "PermissionImpliesUnsupportedHardware",
            "Permission Implies Unsupported Hardware",
            "The `<uses-permission>` element should not require a permission that implies an unsupported TV hardware feature. " +
            "Google Play assumes that certain hardware related permissions indicate that the underlying hardware features are required by default. " +
            "To fix the issue, consider declaring the corresponding `uses-feature` element with `required=\"false\" attribute.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String TAG_USES_PERMISSION = "uses-permission";
    private static final String TAG_USES_FEATURE = "uses-feature";

    private static final Map<String, String> PERMISSION_TO_FEATURE = new HashMap<>();
    static {
        PERMISSION_TO_FEATURE.put("android.permission.CAMERA", "android.hardware.camera");
        PERMISSION_TO_FEATURE.put("android.permission.BLUETOOTH", "android.hardware.bluetooth");
        PERMISSION_TO_FEATURE.put("android.permission.BLUETOOTH_ADMIN", "android.hardware.bluetooth");
        PERMISSION_TO_FEATURE.put("android.permission.NFC", "android.hardware.nfc");
        PERMISSION_TO_FEATURE.put("android.permission.RECORD_AUDIO", "android.hardware.microphone");
        PERMISSION_TO_FEATURE.put("android.permission.CALL_PHONE", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.READ_PHONE_STATE", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.MODIFY_PHONE_STATE", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.PROCESS_OUTGOING_CALLS", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.READ_SMS", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.RECEIVE_SMS", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.SEND_SMS", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.WRITE_SMS", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.ACCESS_COARSE_LOCATION", "android.hardware.location");
        PERMISSION_TO_FEATURE.put("android.permission.ACCESS_FINE_LOCATION", "android.hardware.location");
        PERMISSION_TO_FEATURE.put("android.permission.ACCESS_WIFI_STATE", "android.hardware.wifi");
        PERMISSION_TO_FEATURE.put("android.permission.CHANGE_WIFI_STATE", "android.hardware.wifi");
    }

    private Map<String, Element> requestedPermissions;
    private Set<String> optionalFeatures;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_USES_PERMISSION, TAG_USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(XmlContext context) {
        requestedPermissions = new HashMap<>();
        optionalFeatures = new HashSet<>();
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();
        String name = element.getAttributeNS(ANDROID_URI, "name");
        if (name.isEmpty()) {
            return;
        }

        if (TAG_USES_FEATURE.equals(tag)) {
            String required = element.getAttributeNS(ANDROID_URI, "required");
            if ("false".equals(required)) {
                optionalFeatures.add(name);
            }
        } else if (TAG_USES_PERMISSION.equals(tag)) {
            if (PERMISSION_TO_FEATURE.containsKey(name)) {
                requestedPermissions.put(name, element);
            }
        }
    }

    @Override
    public void afterCheckFile(XmlContext context) {
        for (Map.Entry<String, Element> entry : requestedPermissions.entrySet()) {
            String permission = entry.getKey();
            Element element = entry.getValue();
            String impliedFeature = PERMISSION_TO_FEATURE.get(permission);
            if (impliedFeature != null && !optionalFeatures.contains(impliedFeature)) {
                context.report(ISSUE, element, context.getLocation(element),
                        "Permission " + permission + " implies hardware feature " + impliedFeature +
                        ", which may not be supported on Android TV. Consider adding <uses-feature android:name=\"" +
                        impliedFeature + "\" android:required=\"false\" /> to your manifest.");
            }
        }
    }
}
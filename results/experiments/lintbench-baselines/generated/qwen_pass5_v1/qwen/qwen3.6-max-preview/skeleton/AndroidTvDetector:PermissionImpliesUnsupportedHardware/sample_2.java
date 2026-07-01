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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
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
                    "The <uses-permission> element should not require a permission that implies an unsupported TV hardware feature. " +
                    "Google Play assumes that certain hardware related permissions indicate that the underlying hardware features " +
                    "are required by default. To fix the issue, consider declaring the corresponding uses-feature element with " +
                    "required=\"false\" attribute.",
                    Category.CORRECTNESS,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final Map<String, String> PERMISSION_TO_FEATURE = new HashMap<>();
    static {
        String[] telephonyPerms = {"CALL_PHONE", "CALL_PRIVILEGED", "MODIFY_PHONE_STATE", "PROCESS_OUTGOING_CALLS",
                "READ_SMS", "RECEIVE_SMS", "RECEIVE_MMS", "RECEIVE_WAP_PUSH", "SEND_SMS", "WRITE_APN_SETTINGS", "WRITE_SMS"};
        for (String p : telephonyPerms) {
            PERMISSION_TO_FEATURE.put("android.permission." + p, "android.hardware.telephony");
        }
        PERMISSION_TO_FEATURE.put("android.permission.CAMERA", "android.hardware.camera");
        PERMISSION_TO_FEATURE.put("android.permission.ACCESS_FINE_LOCATION", "android.hardware.location.gps");
        PERMISSION_TO_FEATURE.put("android.permission.RECORD_AUDIO", "android.hardware.microphone");
        PERMISSION_TO_FEATURE.put("android.permission.NFC", "android.hardware.nfc");
        PERMISSION_TO_FEATURE.put("android.permission.BLUETOOTH", "android.hardware.bluetooth");
        PERMISSION_TO_FEATURE.put("android.permission.BLUETOOTH_ADMIN", "android.hardware.bluetooth");
        PERMISSION_TO_FEATURE.put("android.permission.BLUETOOTH_CONNECT", "android.hardware.bluetooth");
        PERMISSION_TO_FEATURE.put("android.permission.BLUETOOTH_ADVERTISE", "android.hardware.bluetooth");
    }

    private final Set<String> optionalFeatures = new HashSet<>();
    private final Map<String, Element> permissions = new HashMap<>();

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("uses-permission", "uses-feature");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        optionalFeatures.clear();
        permissions.clear();
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!(context instanceof XmlContext)) {
            return;
        }
        XmlContext xmlContext = (XmlContext) context;

        for (Map.Entry<String, Element> entry : permissions.entrySet()) {
            String permName = entry.getKey();
            String lookupName = permName.startsWith("android.permission.") ? permName : "android.permission." + permName;
            String feature = PERMISSION_TO_FEATURE.get(lookupName);

            if (feature != null && !optionalFeatures.contains(feature)) {
                String message = String.format(
                        "Permission %s implies unsupported TV hardware feature %s. " +
                        "Google Play assumes that certain hardware related permissions indicate that the " +
                        "underlying hardware features are required by default. To fix the issue, consider " +
                        "declaring the corresponding <uses-feature> element with android:required=\"false\".",
                        permName, feature);
                xmlContext.report(ISSUE, entry.getValue(), xmlContext.getLocation(entry.getValue()), message);
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        String name = element.getAttribute("android:name");
        if (name == null || name.isEmpty()) {
            return;
        }

        if ("uses-feature".equals(tagName)) {
            String required = element.getAttribute("android:required");
            if ("false".equals(required)) {
                optionalFeatures.add(name);
            }
        } else if ("uses-permission".equals(tagName)) {
            permissions.put(name, element);
        }
    }
}
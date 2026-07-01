package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.jetbrains.annotations.NotNull;
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
            "The `<uses-permission>` element should not require a permission that implies an unsupported TV hardware feature. "
                    + "Google Play assumes that certain hardware related permissions indicate that the underlying hardware features are required by default. "
                    + "To fix the issue, consider declaring the corresponding `uses-feature` element with `required=\"false\"` attribute.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private static final Map<String, String> PERMISSION_TO_FEATURE = new HashMap<>();
    static {
        PERMISSION_TO_FEATURE.put("android.permission.CAMERA", "android.hardware.camera");
        PERMISSION_TO_FEATURE.put("android.permission.NFC", "android.hardware.nfc");
        PERMISSION_TO_FEATURE.put("android.permission.ACCESS_FINE_LOCATION", "android.hardware.location.gps");
        PERMISSION_TO_FEATURE.put("android.permission.ACCESS_COARSE_LOCATION", "android.hardware.location.network");
        String[] telephonyPerms = {
                "CALL_PHONE", "CALL_PRIVILEGED", "MODIFY_PHONE_STATE", "PROCESS_OUTGOING_CALLS",
                "READ_SMS", "RECEIVE_SMS", "RECEIVE_MMS", "RECEIVE_WAP_PUSH", "SEND_SMS",
                "WRITE_APN_SETTINGS", "WRITE_SMS"
        };
        for (String perm : telephonyPerms) {
            PERMISSION_TO_FEATURE.put("android.permission." + perm, "android.hardware.telephony");
        }
    }

    private Set<String> optionalFeatures;
    private Map<String, Element> permissionElements;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("uses-permission", "uses-feature");
    }

    @Override
    public void beforeCheckFile(@NotNull Context context) {
        optionalFeatures = new HashSet<>();
        permissionElements = new HashMap<>();
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        String tag = element.getTagName();
        String name = element.getAttribute("android:name");
        if (name == null || name.isEmpty()) {
            return;
        }

        if ("uses-feature".equals(tag)) {
            String required = element.getAttribute("android:required");
            if ("false".equals(required)) {
                optionalFeatures.add(name);
            }
        } else if ("uses-permission".equals(tag)) {
            if (PERMISSION_TO_FEATURE.containsKey(name)) {
                permissionElements.put(name, element);
            }
        }
    }

    @Override
    public void afterCheckFile(@NotNull Context context) {
        if (!(context instanceof XmlContext)) {
            return;
        }
        XmlContext xmlContext = (XmlContext) context;
        for (Map.Entry<String, Element> entry : permissionElements.entrySet()) {
            String perm = entry.getKey();
            String feature = PERMISSION_TO_FEATURE.get(perm);
            if (feature != null && !optionalFeatures.contains(feature)) {
                Element elem = entry.getValue();
                xmlContext.report(ISSUE, elem, xmlContext.getLocation(elem),
                        String.format("Permission %s implies unsupported TV hardware feature %s. "
                                + "Consider declaring <uses-feature android:name=\"%s\" android:required=\"false\" />.",
                                perm, feature, feature));
            }
        }
    }
}
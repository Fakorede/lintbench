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

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "PermissionImpliesUnsupportedHardware",
                    "Permission Implies Unsupported Hardware",
                    "The `<uses-permission>` element should not require a permission that implies"
                            + " an unsupported TV hardware feature. Google Play assumes that"
                            + " certain hardware related permissions indicate that the underlying"
                            + " hardware features are required by default. To fix the issue, consider"
                            + " declaring the corresponding `<uses-feature>` element with"
                            + " `required=\"false\"` attribute.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private static final java.util.Map<String, String> PERMISSION_TO_FEATURE;

    static {
        java.util.Map<String, String> map = new java.util.HashMap<>();
        map.put("android.permission.ACCESS_FINE_LOCATION", "android.hardware.location.gps");
        map.put("android.permission.CAMERA", "android.hardware.camera");
        map.put("android.permission.RECORD_AUDIO", "android.hardware.microphone");
        map.put("android.permission.CALL_PHONE", "android.hardware.telephony");
        map.put("android.permission.CALL_PRIVILEGED", "android.hardware.telephony");
        map.put("android.permission.MODIFY_PHONE_STATE", "android.hardware.telephony");
        map.put("android.permission.PROCESS_OUTGOING_CALLS", "android.hardware.telephony");
        map.put("android.permission.READ_SMS", "android.hardware.telephony");
        map.put("android.permission.RECEIVE_SMS", "android.hardware.telephony");
        map.put("android.permission.RECEIVE_MMS", "android.hardware.telephony");
        map.put("android.permission.RECEIVE_WAP_PUSH", "android.hardware.telephony");
        map.put("android.permission.SEND_SMS", "android.hardware.telephony");
        map.put("android.permission.WRITE_SMS", "android.hardware.telephony");
        map.put("android.permission.BROADCAST_SMS", "android.hardware.telephony");
        map.put("android.permission.BROADCAST_WAP_PUSH", "android.hardware.telephony");
        PERMISSION_TO_FEATURE = java.util.Collections.unmodifiableMap(map);
    }

    private java.util.List<org.w3c.dom.Element> mPermissionElements;

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Collections.singletonList("uses-permission");
    }

    @Override
    public void beforeCheckFile(Context context) {
        mPermissionElements = new java.util.ArrayList<>();
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        mPermissionElements.add(element);
    }

    @Override
    public void afterCheckFile(Context context) {
        if (mPermissionElements == null || mPermissionElements.isEmpty()) {
            return;
        }

        XmlContext xmlContext = (XmlContext) context;

        java.util.Set<String> declaredNotRequired = new java.util.HashSet<>();
        org.w3c.dom.NodeList features =
                xmlContext.getDocument().getElementsByTagName("uses-feature");
        for (int i = 0, n = features.getLength(); i < n; i++) {
            org.w3c.dom.Element featureElement = (org.w3c.dom.Element) features.item(i);
            String name = featureElement.getAttribute("android:name");
            if (name.isEmpty()) {
                name = featureElement.getAttribute("name");
            }
            String required = featureElement.getAttribute("android:required");
            if (required.isEmpty()) {
                required = featureElement.getAttribute("required");
            }
            if (!name.isEmpty() && "false".equals(required)) {
                declaredNotRequired.add(name);
            }
        }

        for (org.w3c.dom.Element permissionElement : mPermissionElements) {
            String name = permissionElement.getAttribute("android:name");
            if (name.isEmpty()) {
                name = permissionElement.getAttribute("name");
            }
            String feature = PERMISSION_TO_FEATURE.get(name);
            if (feature == null) {
                continue;
            }
            if (!declaredNotRequired.contains(feature)) {
                String message =
                        "Permission `"
                                + name
                                + "` implies unsupported TV hardware feature `"
                                + feature
                                + "`; declare the corresponding `<uses-feature>` with"
                                + " `required=\"false\"`";
                xmlContext.report(
                        ISSUE,
                        permissionElement,
                        xmlContext.getLocation(permissionElement),
                        message);
            }
        }

        mPermissionElements = null;
    }
}
package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final java.util.Map<String, java.util.List<String>> PERMISSION_TO_FEATURES;
    static {
        java.util.Map<String, java.util.List<String>> map = new java.util.HashMap<>();
        add(map, "android.permission.CAMERA",
                "android.hardware.camera",
                "android.hardware.camera.autofocus");
        add(map, "android.permission.RECORD_AUDIO",
                "android.hardware.microphone");
        add(map, "android.permission.ACCESS_FINE_LOCATION",
                "android.hardware.location.gps");
        add(map, "android.permission.ACCESS_COARSE_LOCATION",
                "android.hardware.location.network");
        add(map, "android.permission.CALL_PHONE",
                "android.hardware.telephony");
        add(map, "android.permission.READ_PHONE_STATE",
                "android.hardware.telephony");
        add(map, "android.permission.SEND_SMS",
                "android.hardware.telephony");
        add(map, "android.permission.RECEIVE_SMS",
                "android.hardware.telephony");
        add(map, "android.permission.READ_SMS",
                "android.hardware.telephony");
        add(map, "android.permission.WRITE_SMS",
                "android.hardware.telephony");
        add(map, "android.permission.RECEIVE_WAP_PUSH",
                "android.hardware.telephony");
        add(map, "android.permission.RECEIVE_MMS",
                "android.hardware.telephony");
        add(map, "android.permission.BLUETOOTH",
                "android.hardware.bluetooth");
        add(map, "android.permission.BLUETOOTH_ADMIN",
                "android.hardware.bluetooth");
        PERMISSION_TO_FEATURES = java.util.Collections.unmodifiableMap(map);
    }

    public static final Issue ISSUE =
            Issue.create(
                    "PermissionImpliesUnsupportedHardware",
                    "Permission Implies Unsupported Hardware",
                    "The `<uses-permission>` element should not require a permission that implies"
                            + " an unsupported TV hardware feature. Google Play assumes that"
                            + " certain hardware-related permissions indicate that the underlying"
                            + " hardware features are required by default. To fix the issue,"
                            + " consider declaring the corresponding `<uses-feature>` element with"
                            + " `required=\"false\"`.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private java.util.Set<String> mRequiredFalseFeatures;
    private java.util.List<PermissionInfo> mPermissionInfos;

    private static class PermissionInfo {
        final String name;
        final org.w3c.dom.Element element;

        PermissionInfo(String name, org.w3c.dom.Element element) {
            this.name = name;
            this.element = element;
        }
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        java.util.List<String> elements = new java.util.ArrayList<>(2);
        elements.add(com.android.xml.AndroidManifest.NODE_USES_PERMISSION);
        elements.add(com.android.xml.AndroidManifest.NODE_USES_FEATURE);
        return elements;
    }

    @Override
    public void beforeCheckFile(XmlContext context) {
        mRequiredFalseFeatures = new java.util.HashSet<>();
        mPermissionInfos = new java.util.ArrayList<>();
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        String tag = element.getLocalName();
        if (tag == null) {
            tag = element.getNodeName();
        }

        if (com.android.xml.AndroidManifest.NODE_USES_PERMISSION.equals(tag)) {
            String permission = element.getAttributeNS(ANDROID_URI, "name").trim();
            if (!permission.isEmpty() && PERMISSION_TO_FEATURES.containsKey(permission)) {
                mPermissionInfos.add(new PermissionInfo(permission, element));
            }
        } else if (com.android.xml.AndroidManifest.NODE_USES_FEATURE.equals(tag)) {
            String feature = element.getAttributeNS(ANDROID_URI, "name").trim();
            String required = element.getAttributeNS(ANDROID_URI, "required");
            if (!feature.isEmpty() && "false".equals(required)) {
                mRequiredFalseFeatures.add(feature);
            }
        }
    }

    @Override
    public void afterCheckFile(XmlContext context) {
        for (PermissionInfo info : mPermissionInfos) {
            java.util.List<String> features = PERMISSION_TO_FEATURES.get(info.name);
            if (features == null) {
                continue;
            }

            java.util.List<String> missing = new java.util.ArrayList<>();
            for (String feature : features) {
                if (!mRequiredFalseFeatures.contains(feature)) {
                    missing.add(feature);
                }
            }

            if (!missing.isEmpty()) {
                StringBuilder message = new StringBuilder();
                message.append("Permission `").append(info.name).append("` implies ");
                if (missing.size() == 1) {
                    message.append("the hardware feature `")
                            .append(missing.get(0))
                            .append("` which is not supported on TV.");
                } else {
                    message.append("the hardware features ")
                            .append(join(missing))
                            .append(" which are not supported on TV.");
                }
                message.append(" Consider declaring the corresponding `<uses-feature>`")
                        .append(" element(s) with `required=\"false\"`.");

                context.report(
                        ISSUE,
                        info.element,
                        context.getLocation(info.element),
                        message.toString());
            }
        }
    }

    private static void add(java.util.Map<String, java.util.List<String>> map,
            String permission, String... features) {
        java.util.List<String> list = new java.util.ArrayList<>(features.length);
        for (String feature : features) {
            list.add(feature);
        }
        map.put(permission, java.util.Collections.unmodifiableList(list));
    }

    private static String join(java.util.List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                if (i == list.size() - 1) {
                    sb.append(" and ");
                } else {
                    sb.append(", ");
                }
            }
            sb.append("`").append(list.get(i)).append("`");
        }
        return sb.toString();
    }
}
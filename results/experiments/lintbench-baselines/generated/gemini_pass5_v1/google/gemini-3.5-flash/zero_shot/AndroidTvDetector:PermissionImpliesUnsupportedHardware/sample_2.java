package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "PermissionImpliesUnsupportedHardware",
            "Permission Implies Unsupported Hardware",
            "The `<uses-permission>` element should not require a permission that " +
            "implies an unsupported TV hardware feature. Google Play assumes " +
            "that certain hardware related permissions indicate that the " +
            "underlying hardware features are required by default. To fix " +
            "the issue, consider declaring the corresponding `uses-feature` " +
            "element with `required=\"false\"` attribute.",
            Category.COMPATIBILITY,
            5,
            Severity.WARNING,
            new Implementation(
                    AndroidTvDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    );

    private static final Map<String, String[]> IMPLIED_FEATURES = new HashMap<>();

    static {
        IMPLIED_FEATURES.put("android.permission.CAMERA", new String[] {
            "android.hardware.camera", "android.hardware.camera.autofocus"
        });
        IMPLIED_FEATURES.put("android.permission.RECORD_AUDIO", new String[] {
            "android.hardware.microphone"
        });
        IMPLIED_FEATURES.put("android.permission.ACCESS_FINE_LOCATION", new String[] {
            "android.hardware.location", "android.hardware.location.gps"
        });
        IMPLIED_FEATURES.put("android.permission.ACCESS_COARSE_LOCATION", new String[] {
            "android.hardware.location", "android.hardware.location.network"
        });
        IMPLIED_FEATURES.put("android.permission.SEND_SMS", new String[] { "android.hardware.telephony" });
        IMPLIED_FEATURES.put("android.permission.RECEIVE_SMS", new String[] { "android.hardware.telephony" });
        IMPLIED_FEATURES.put("android.permission.READ_SMS", new String[] { "android.hardware.telephony" });
        IMPLIED_FEATURES.put("android.permission.RECEIVE_WAP_PUSH", new String[] { "android.hardware.telephony" });
        IMPLIED_FEATURES.put("android.permission.RECEIVE_MMS", new String[] { "android.hardware.telephony" });
        IMPLIED_FEATURES.put("android.permission.CALL_PHONE", new String[] { "android.hardware.telephony" });
        IMPLIED_FEATURES.put("android.permission.PROCESS_OUTGOING_CALLS", new String[] { "android.hardware.telephony" });
        IMPLIED_FEATURES.put("android.permission.CALL_PRIVILEGED", new String[] { "android.hardware.telephony" });
        IMPLIED_FEATURES.put("android.permission.MODIFY_PHONE_STATE", new String[] { "android.hardware.telephony" });
        IMPLIED_FEATURES.put("android.permission.BLUETOOTH", new String[] { "android.hardware.bluetooth" });
        IMPLIED_FEATURES.put("android.permission.BLUETOOTH_ADMIN", new String[] { "android.hardware.bluetooth" });
        IMPLIED_FEATURES.put("android.permission.NFC", new String[] { "android.hardware.nfc" });
    }

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        if (isTvApp(document)) {
            checkPermissions(context, document);
        }
    }

    private boolean isTvApp(Document document) {
        NodeList usesFeatures = document.getElementsByTagName("uses-feature");
        for (int i = 0; i < usesFeatures.getLength(); i++) {
            Element element = (Element) usesFeatures.item(i);
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if ("android.software.leanback".equals(name)) {
                return true;
            }
        }
        NodeList categories = document.getElementsByTagName("category");
        for (int i = 0; i < categories.getLength(); i++) {
            Element element = (Element) categories.item(i);
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                return true;
            }
        }
        return false;
    }

    private void checkPermissions(XmlContext context, Document document) {
        Set<String> safeFeatures = new HashSet<>();
        NodeList usesFeatures = document.getElementsByTagName("uses-feature");
        for (int i = 0; i < usesFeatures.getLength(); i++) {
            Element element = (Element) usesFeatures.item(i);
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            String requiredStr = element.getAttributeNS("http://schemas.android.com/apk/res/android", "required");
            boolean required = requiredStr.isEmpty() || Boolean.parseBoolean(requiredStr);
            if (!name.isEmpty() && !required) {
                safeFeatures.add(name);
            }
        }

        checkPermissionElements(context, document.getElementsByTagName("uses-permission"), safeFeatures);
        checkPermissionElements(context, document.getElementsByTagName("uses-permission-sdk-23"), safeFeatures);
    }

    private void checkPermissionElements(XmlContext context, NodeList elements, Set<String> safeFeatures) {
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            String permissionName = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if (IMPLIED_FEATURES.containsKey(permissionName)) {
                String[] implied = IMPLIED_FEATURES.get(permissionName);
                List<String> missingFeatures = new ArrayList<>();
                for (String feature : implied) {
                    if (!safeFeatures.contains(feature)) {
                        missingFeatures.add(feature);
                    }
                }
                if (!missingFeatures.isEmpty()) {
                    String message;
                    if (missingFeatures.size() == 1) {
                        String feature = missingFeatures.get(0);
                        message = String.format(
                                "Permission `%1$s` implies `%2$s` hardware feature, which is not " +
                                "supported on TV. Consider declaring `<uses-feature android:name=\"%2$s\" " +
                                "android:required=\"false\" />`.",
                                permissionName, feature);
                    } else {
                        StringBuilder sb = new StringBuilder();
                        sb.append("Permission `").append(permissionName).append("` implies unsupported TV hardware features: ");
                        for (int j = 0; j < missingFeatures.size(); j++) {
                            if (j > 0) {
                                sb.append(", ");
                            }
                            sb.append("`").append(missingFeatures.get(j)).append("`");
                        }
                        sb.append(". Consider declaring them with `android:required=\"false\"`.");
                        message = sb.toString();
                    }
                    context.report(ISSUE, element, context.getNameLocation(element), message);
                }
            }
        }
    }
}
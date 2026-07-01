package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(
                    AndroidTvDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    );

    private static final Map<String, List<String>> IMPLIED_FEATURES = new HashMap<>();
    static {
        IMPLIED_FEATURES.put("android.permission.CAMERA", Arrays.asList("android.hardware.camera", "android.hardware.camera.autofocus"));
        IMPLIED_FEATURES.put("android.permission.RECORD_AUDIO", Arrays.asList("android.hardware.microphone"));
        IMPLIED_FEATURES.put("android.permission.ACCESS_FINE_LOCATION", Arrays.asList("android.hardware.location", "android.hardware.location.gps"));
        IMPLIED_FEATURES.put("android.permission.ACCESS_COARSE_LOCATION", Arrays.asList("android.hardware.location", "android.hardware.location.network"));
        
        List<String> telephony = Arrays.asList("android.hardware.telephony");
        IMPLIED_FEATURES.put("android.permission.CALL_PHONE", telephony);
        IMPLIED_FEATURES.put("android.permission.SEND_SMS", telephony);
        IMPLIED_FEATURES.put("android.permission.RECEIVE_SMS", telephony);
        IMPLIED_FEATURES.put("android.permission.RECEIVE_MMS", telephony);
        IMPLIED_FEATURES.put("android.permission.RECEIVE_WAP_PUSH", telephony);
        IMPLIED_FEATURES.put("android.permission.READ_SMS", telephony);
        IMPLIED_FEATURES.put("android.permission.WRITE_SMS", telephony);
        IMPLIED_FEATURES.put("android.permission.PROCESS_OUTGOING_CALLS", telephony);
        IMPLIED_FEATURES.put("android.permission.READ_PHONE_STATE", telephony);
        IMPLIED_FEATURES.put("android.permission.MODIFY_PHONE_STATE", telephony);
        IMPLIED_FEATURES.put("android.permission.USE_SIP", telephony);
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        boolean isTvApp = false;
        NodeList usesFeatures = document.getElementsByTagName("uses-feature");
        Map<String, Boolean> declaredFeatures = new HashMap<>();
        
        for (int i = 0; i < usesFeatures.getLength(); i++) {
            Element element = (Element) usesFeatures.item(i);
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if ("android.software.leanback".equals(name)) {
                isTvApp = true;
            }
            String requiredStr = element.getAttributeNS("http://schemas.android.com/apk/res/android", "required");
            boolean required = requiredStr.isEmpty() || Boolean.parseBoolean(requiredStr);
            declaredFeatures.put(name, required);
        }

        if (!isTvApp) {
            NodeList categories = document.getElementsByTagName("category");
            for (int i = 0; i < categories.getLength(); i++) {
                Element element = (Element) categories.item(i);
                String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
                if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                    isTvApp = true;
                    break;
                }
            }
        }

        if (!isTvApp) {
            return;
        }

        checkPermissions(context, document, "uses-permission", declaredFeatures);
        checkPermissions(context, document, "uses-permission-sdk-23", declaredFeatures);
    }

    private void checkPermissions(XmlContext context, Document document, String tagName, Map<String, Boolean> declaredFeatures) {
        NodeList permissions = document.getElementsByTagName(tagName);
        for (int i = 0; i < permissions.getLength(); i++) {
            Element element = (Element) permissions.item(i);
            String permissionName = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            List<String> implied = IMPLIED_FEATURES.get(permissionName);
            if (implied != null) {
                for (String feature : implied) {
                    Boolean required = declaredFeatures.get(feature);
                    if (required == null || required) {
                        String message = String.format(
                                "Permission `%1$s` implies `%2$s` hardware but it is not declared as optional in the manifest. " +
                                "Add `<uses-feature android:name=\"%2$s\" android:required=\"false\" />` to the manifest.",
                                permissionName, feature);
                        context.report(ISSUE, element, context.getLocation(element), message);
                    }
                }
            }
        }
    }
}
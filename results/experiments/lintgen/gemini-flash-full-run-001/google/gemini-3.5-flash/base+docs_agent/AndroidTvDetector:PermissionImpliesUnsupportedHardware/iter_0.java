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
import java.util.Arrays;
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
        Category.CORRECTNESS,
        6,
        Severity.WARNING,
        new Implementation(
            AndroidTvDetector.class,
            Scope.MANIFEST_SCOPE
        )
    );

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final Map<String, List<String>> IMPLIED_FEATURES = new HashMap<>();
    static {
        IMPLIED_FEATURES.put("android.permission.CAMERA", Arrays.asList(
            "android.hardware.camera",
            "android.hardware.camera.autofocus"
        ));
        IMPLIED_FEATURES.put("android.permission.RECORD_AUDIO", Arrays.asList(
            "android.hardware.microphone"
        ));
        IMPLIED_FEATURES.put("android.permission.ACCESS_FINE_LOCATION", Arrays.asList(
            "android.hardware.location",
            "android.hardware.location.gps"
        ));
        IMPLIED_FEATURES.put("android.permission.ACCESS_COARSE_LOCATION", Arrays.asList(
            "android.hardware.location",
            "android.hardware.location.network"
        ));
        List<String> telephonyFeatures = Arrays.asList("android.hardware.telephony");
        IMPLIED_FEATURES.put("android.permission.SEND_SMS", telephonyFeatures);
        IMPLIED_FEATURES.put("android.permission.RECEIVE_SMS", telephonyFeatures);
        IMPLIED_FEATURES.put("android.permission.READ_SMS", telephonyFeatures);
        IMPLIED_FEATURES.put("android.permission.RECEIVE_WAP_PUSH", telephonyFeatures);
        IMPLIED_FEATURES.put("android.permission.RECEIVE_MMS", telephonyFeatures);
        IMPLIED_FEATURES.put("android.permission.CALL_PHONE", telephonyFeatures);
        IMPLIED_FEATURES.put("android.permission.PROCESS_OUTGOING_CALLS", telephonyFeatures);
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        boolean isTvApp = false;
        NodeList usesFeatures = document.getElementsByTagName("uses-feature");
        
        for (int i = 0; i < usesFeatures.getLength(); i++) {
            Element element = (Element) usesFeatures.item(i);
            String name = element.getAttributeNS(ANDROID_URI, "name");
            if ("android.software.leanback".equals(name)) {
                isTvApp = true;
            }
            if ("android.hardware.touchscreen".equals(name)) {
                String required = element.getAttributeNS(ANDROID_URI, "required");
                if ("false".equals(required)) {
                    isTvApp = true;
                }
            }
        }

        if (!isTvApp) {
            NodeList categories = document.getElementsByTagName("category");
            for (int i = 0; i < categories.getLength(); i++) {
                Element element = (Element) categories.item(i);
                String name = element.getAttributeNS(ANDROID_URI, "name");
                if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                    isTvApp = true;
                    break;
                }
            }
        }

        if (!isTvApp) {
            return;
        }

        Set<String> optionalFeatures = new HashSet<>();
        for (int i = 0; i < usesFeatures.getLength(); i++) {
            Element element = (Element) usesFeatures.item(i);
            String name = element.getAttributeNS(ANDROID_URI, "name");
            String required = element.getAttributeNS(ANDROID_URI, "required");
            if ("false".equals(required)) {
                optionalFeatures.add(name);
            }
        }

        List<Element> permissionElements = new ArrayList<>();
        NodeList permissions = document.getElementsByTagName("uses-permission");
        for (int i = 0; i < permissions.getLength(); i++) {
            permissionElements.add((Element) permissions.item(i));
        }
        NodeList permissions23 = document.getElementsByTagName("uses-permission-sdk-23");
        for (int i = 0; i < permissions23.getLength(); i++) {
            permissionElements.add((Element) permissions23.item(i));
        }

        for (Element element : permissionElements) {
            String permissionName = element.getAttributeNS(ANDROID_URI, "name");
            if (IMPLIED_FEATURES.containsKey(permissionName)) {
                List<String> implied = IMPLIED_FEATURES.get(permissionName);
                for (String feature : implied) {
                    if (!optionalFeatures.contains(feature)) {
                        String message = String.format(
                            "Permission `%1$s` implies `%2$s` hardware, which is not " +
                            "supported on TVs. Consider adding `<uses-feature android:name=\"%2$s\" android:required=\"false\" />` to the manifest.",
                            permissionName, feature
                        );
                        context.report(ISSUE, element, context.getLocation(element), message);
                    }
                }
            }
        }
    }
}
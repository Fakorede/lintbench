package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_REQUIRED;

import com.android.annotations.NonNull;
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
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
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
        IMPLIED_FEATURES.put("android.permission.CAMERA", Arrays.asList(
                "android.hardware.camera",
                "android.hardware.camera.autofocus"
        ));
        IMPLIED_FEATURES.put("android.permission.RECORD_AUDIO", Arrays.asList(
                "android.hardware.microphone"
        ));
        IMPLIED_FEATURES.put("android.permission.ACCESS_COARSE_LOCATION", Arrays.asList(
                "android.hardware.location",
                "android.hardware.location.network"
        ));
        IMPLIED_FEATURES.put("android.permission.ACCESS_FINE_LOCATION", Arrays.asList(
                "android.hardware.location",
                "android.hardware.location.gps"
        ));
        List<String> telephony = Arrays.asList("android.hardware.telephony");
        IMPLIED_FEATURES.put("android.permission.SEND_SMS", telephony);
        IMPLIED_FEATURES.put("android.permission.RECEIVE_SMS", telephony);
        IMPLIED_FEATURES.put("android.permission.RECEIVE_MMS", telephony);
        IMPLIED_FEATURES.put("android.permission.RECEIVE_WAP_PUSH", telephony);
        IMPLIED_FEATURES.put("android.permission.SEND_RESPOND_VIA_MESSAGE", telephony);
        IMPLIED_FEATURES.put("android.permission.CALL_PHONE", telephony);
        IMPLIED_FEATURES.put("android.permission.PROCESS_OUTGOING_CALLS", telephony);
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        boolean isTvApp = false;
        List<Element> permissionElements = new ArrayList<>();
        Set<String> featuresNotRequired = new HashSet<>();

        NodeList usesPermissions = root.getElementsByTagName("uses-permission");
        for (int i = 0; i < usesPermissions.getLength(); i++) {
            Node node = usesPermissions.item(i);
            if (node instanceof Element) {
                permissionElements.add((Element) node);
            }
        }

        NodeList usesFeatures = root.getElementsByTagName("uses-feature");
        for (int i = 0; i < usesFeatures.getLength(); i++) {
            Node node = usesFeatures.item(i);
            if (node instanceof Element) {
                Element element = (Element) node;
                String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
                if ("android.software.leanback".equals(name)) {
                    isTvApp = true;
                }
                String requiredStr = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
                if ("false".equalsIgnoreCase(requiredStr)) {
                    featuresNotRequired.add(name);
                }
            }
        }

        NodeList categories = root.getElementsByTagName("category");
        for (int i = 0; i < categories.getLength(); i++) {
            Node node = categories.item(i);
            if (node instanceof Element) {
                Element element = (Element) node;
                String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
                if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                    isTvApp = true;
                }
            }
        }

        if (!isTvApp) {
            return;
        }

        for (Element permissionElement : permissionElements) {
            String permissionName = permissionElement.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (permissionName == null || permissionName.isEmpty()) {
                continue;
            }

            List<String> implied = IMPLIED_FEATURES.get(permissionName);
            if (implied != null) {
                for (String feature : implied) {
                    if (!featuresNotRequired.contains(feature)) {
                        Attr nameAttr = permissionElement.getAttributeNodeNS(ANDROID_URI, ATTR_NAME);
                        context.report(
                                ISSUE,
                                permissionElement,
                                context.getLocation(nameAttr != null ? nameAttr : permissionElement),
                                String.format("Permission `%1$s` implies required hardware `%2$s` which is not " +
                                        "supported on Android TV. Consider adding `<uses-feature android:name=\"%2$s\" android:required=\"false\" />`.",
                                        permissionName, feature)
                        );
                    }
                }
            }
        }
    }
}
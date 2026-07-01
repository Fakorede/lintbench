package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.HashMap;
import java.util.Map;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

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

    private static final Map<String, String[]> IMPLIES = new HashMap<>();

    static {
        IMPLIES.put("android.permission.CAMERA", new String[]{"android.hardware.camera"});
        IMPLIES.put("android.permission.RECORD_AUDIO", new String[]{"android.hardware.microphone"});
        IMPLIES.put("android.permission.ACCESS_FINE_LOCATION", new String[]{"android.hardware.location", "android.hardware.location.gps"});
        IMPLIES.put("android.permission.ACCESS_COARSE_LOCATION", new String[]{"android.hardware.location"});
        IMPLIES.put("android.permission.CALL_PHONE", new String[]{"android.hardware.telephony"});
        IMPLIES.put("android.permission.SEND_SMS", new String[]{"android.hardware.telephony"});
        IMPLIES.put("android.permission.RECEIVE_SMS", new String[]{"android.hardware.telephony"});
        IMPLIES.put("android.permission.RECEIVE_MMS", new String[]{"android.hardware.telephony"});
        IMPLIES.put("android.permission.RECEIVE_WAP_PUSH", new String[]{"android.hardware.telephony"});
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        if (!isTvApp(document)) {
            return;
        }

        Map<String, Boolean> features = new HashMap<>();
        NodeList usesFeatures = document.getElementsByTagName("uses-feature");
        for (int i = 0; i < usesFeatures.getLength(); i++) {
            Element element = (Element) usesFeatures.item(i);
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if (name != null && !name.isEmpty()) {
                String requiredAttr = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_REQUIRED);
                boolean required = !"false".equalsIgnoreCase(requiredAttr);
                features.put(name, required);
            }
        }

        NodeList usesPermissions = document.getElementsByTagName("uses-permission");
        for (int i = 0; i < usesPermissions.getLength(); i++) {
            Element element = (Element) usesPermissions.item(i);
            String permissionName = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if (permissionName != null && IMPLIES.containsKey(permissionName)) {
                String[] impliedFeatures = IMPLIES.get(permissionName);
                for (String impliedFeature : impliedFeatures) {
                    if (!features.containsKey(impliedFeature) || features.get(impliedFeature)) {
                        String message = String.format(
                            "Permission `%1$s` implies `%2$s` hardware, which is not " +
                            "supported on TVs. Consider adding `<uses-feature android:name=\"%2$s\" android:required=\"false\" />` to the manifest.",
                            permissionName, impliedFeature
                        );
                        context.report(ISSUE, element, context.getLocation(element), message);
                    }
                }
            }
        }
    }

    private boolean isTvApp(Document document) {
        NodeList usesFeatures = document.getElementsByTagName("uses-feature");
        for (int i = 0; i < usesFeatures.getLength(); i++) {
            Element element = (Element) usesFeatures.item(i);
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if ("android.software.leanback".equals(name)) {
                return true;
            }
        }
        NodeList categories = document.getElementsByTagName("category");
        for (int i = 0; i < categories.getLength(); i++) {
            Element element = (Element) categories.item(i);
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                return true;
            }
        }
        return false;
    }
}
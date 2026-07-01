package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
            3,
            Severity.WARNING,
            new Implementation(
                    AndroidTvDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    );

    private static final Map<String, String[]> IMPLIED_FEATURES = new HashMap<>();

    static {
        IMPLIED_FEATURES.put("android.permission.CAMERA", new String[]{
                "android.hardware.camera",
                "android.hardware.camera.autofocus"
        });
        IMPLIED_FEATURES.put("android.permission.RECORD_AUDIO", new String[]{
                "android.hardware.microphone"
        });
        IMPLIED_FEATURES.put("android.permission.ACCESS_FINE_LOCATION", new String[]{
                "android.hardware.location.gps",
                "android.hardware.location"
        });
        IMPLIED_FEATURES.put("android.permission.ACCESS_COARSE_LOCATION", new String[]{
                "android.hardware.location.network",
                "android.hardware.location"
        });
        IMPLIED_FEATURES.put("android.permission.CALL_PHONE", new String[]{
                "android.hardware.telephony"
        });
        IMPLIED_FEATURES.put("android.permission.SEND_SMS", new String[]{
                "android.hardware.telephony"
        });
        IMPLIED_FEATURES.put("android.permission.RECEIVE_SMS", new String[]{
                "android.hardware.telephony"
        });
        IMPLIED_FEATURES.put("android.permission.RECEIVE_MMS", new String[]{
                "android.hardware.telephony"
        });
        IMPLIED_FEATURES.put("android.permission.RECEIVE_WAP_PUSH", new String[]{
                "android.hardware.telephony"
        });
        IMPLIED_FEATURES.put("android.permission.READ_SMS", new String[]{
                "android.hardware.telephony"
        });
        IMPLIED_FEATURES.put("android.permission.WRITE_SMS", new String[]{
                "android.hardware.telephony"
        });
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        boolean isTvApp = false;
        Set<String> explicitNotRequiredFeatures = new HashSet<>();

        NodeList usesFeatures = root.getElementsByTagName("uses-feature");
        for (int i = 0; i < usesFeatures.getLength(); i++) {
            Element element = (Element) usesFeatures.item(i);
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            String requiredStr = element.getAttributeNS(SdkConstants.ANDROID_URI, "required");
            boolean required = requiredStr.isEmpty() || Boolean.parseBoolean(requiredStr);

            if ("android.software.leanback".equals(name)) {
                isTvApp = true;
            }

            if (!required) {
                explicitNotRequiredFeatures.add(name);
            }
        }

        if (!isTvApp) {
            NodeList categories = root.getElementsByTagName("category");
            for (int i = 0; i < categories.getLength(); i++) {
                Element element = (Element) categories.item(i);
                String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                    isTvApp = true;
                    break;
                }
            }
        }

        if (!isTvApp) {
            return;
        }

        String[] tags = {"uses-permission", "uses-permission-sdk-23"};
        for (String tag : tags) {
            NodeList permissions = root.getElementsByTagName(tag);
            for (int i = 0; i < permissions.getLength(); i++) {
                Element element = (Element) permissions.item(i);
                String permissionName = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                if (IMPLIED_FEATURES.containsKey(permissionName)) {
                    String[] implied = IMPLIED_FEATURES.get(permissionName);
                    for (String feature : implied) {
                        if (!explicitNotRequiredFeatures.contains(feature)) {
                            context.report(
                                    ISSUE,
                                    element,
                                    context.getLocation(element),
                                    "Permission `" + permissionName + "` implies `" + feature + "` hardware, " +
                                    "which is not supported on TV. Consider adding `<uses-feature " +
                                    "android:name=\"" + feature + "\" android:required=\"false\" />`."
                            );
                        }
                    }
                }
            }
        }
    }
}
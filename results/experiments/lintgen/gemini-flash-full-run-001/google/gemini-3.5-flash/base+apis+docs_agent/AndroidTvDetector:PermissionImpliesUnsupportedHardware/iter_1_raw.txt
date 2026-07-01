package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
            Category.COMPLIANCE,
            6,
            Severity.WARNING,
            new Implementation(
                    AndroidTvDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    );

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        if (!context.getProject().isAndroidProject() || !context.file.getName().equals(SdkConstants.FN_ANDROID_MANIFEST_XML)) {
            return;
        }

        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        boolean isTvApp = false;
        List<Element> permissionElements = new ArrayList<>();
        Map<String, Boolean> featureRequiredMap = new HashMap<>();

        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) node;
            String tagName = element.getTagName();
            if (SdkConstants.TAG_USES_PERMISSION.equals(tagName) || "uses-permission-sdk-23".equals(tagName)) {
                permissionElements.add(element);
            } else if (SdkConstants.TAG_USES_FEATURE.equals(tagName)) {
                String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                String requiredStr = element.getAttributeNS(SdkConstants.ANDROID_URI, "required");
                boolean required = requiredStr.isEmpty() || Boolean.parseBoolean(requiredStr);
                featureRequiredMap.put(name, required);
                if ("android.software.leanback".equals(name)) {
                    isTvApp = true;
                }
            } else if (SdkConstants.TAG_APPLICATION.equals(tagName)) {
                if (hasLeanbackLauncher(element)) {
                    isTvApp = true;
                }
            }
        }

        if (!isTvApp) {
            return;
        }

        for (Element permissionElement : permissionElements) {
            String permissionName = permissionElement.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            String impliedFeature = getImpliedFeature(permissionName);
            if (impliedFeature != null) {
                Boolean required = featureRequiredMap.get(impliedFeature);
                if (required == null || required) {
                    String message = String.format(
                        "Permission `%1$s` implies `%2$s` hardware, which is not " +
                        "supported on all Android TV devices. You should explicitly " +
                        "declare `<uses-feature android:name=\"%2$s\" android:required=\"false\" />`.",
                        permissionName, impliedFeature
                    );
                    context.report(ISSUE, permissionElement, context.getLocation(permissionElement), message);
                }
            }
        }
    }

    private boolean hasLeanbackLauncher(Element applicationElement) {
        NodeList activities = applicationElement.getElementsByTagName(SdkConstants.TAG_ACTIVITY);
        for (int i = 0; i < activities.getLength(); i++) {
            Element activity = (Element) activities.item(i);
            NodeList intentFilters = activity.getElementsByTagName(SdkConstants.TAG_INTENT_FILTER);
            for (int j = 0; j < intentFilters.getLength(); j++) {
                Element filter = (Element) intentFilters.item(j);
                NodeList categories = filter.getElementsByTagName(SdkConstants.TAG_CATEGORY);
                for (int k = 0; k < categories.getLength(); k++) {
                    Element category = (Element) categories.item(k);
                    String categoryName = category.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                    if ("android.intent.category.LEANBACK_LAUNCHER".equals(categoryName)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static String getImpliedFeature(String permission) {
        switch (permission) {
            case "android.permission.CAMERA":
                return "android.hardware.camera";
            case "android.permission.RECORD_AUDIO":
                return "android.hardware.microphone";
            case "android.permission.ACCESS_FINE_LOCATION":
            case "android.permission.ACCESS_COARSE_LOCATION":
                return "android.hardware.location";
            case "android.permission.SEND_SMS":
            case "android.permission.RECEIVE_SMS":
            case "android.permission.READ_SMS":
            case "android.permission.RECEIVE_WAP_PUSH":
            case "android.permission.RECEIVE_MMS":
            case "android.permission.CALL_PHONE":
            case "android.permission.PROCESS_OUTGOING_CALLS":
            case "android.permission.READ_PHONE_STATE":
            case "android.permission.MODIFY_PHONE_STATE":
                return "android.hardware.telephony";
            default:
                return null;
        }
    }
}
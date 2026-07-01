package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "PermissionImpliesUnsupportedHardware",
            "Permission implies unsupported TV hardware feature",
            "Certain permissions imply that the corresponding hardware feature is required by " +
                    "default. On Android TV, features such as camera, microphone, GPS, and " +
                    "telephony are not supported. If your app is intended to run on TV, add a " +
                    "`<uses-feature>` element with `android:required=\"false\"` for the implied " +
                    "feature, or remove the permission.",
            Category.TV,
            6,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE),
            "https://developer.android.com/guide/topics/manifest/uses-feature-element.html#permissions"
    );

    private static final Map<String, String> PERMISSION_TO_FEATURE;

    static {
        Map<String, String> map = new HashMap<>();
        map.put("android.permission.CAMERA", "android.hardware.camera");
        map.put("android.permission.RECORD_AUDIO", "android.hardware.microphone");
        map.put("android.permission.ACCESS_FINE_LOCATION", "android.hardware.location.gps");
        map.put("android.permission.ACCESS_COARSE_LOCATION", "android.hardware.location.network");
        map.put("android.permission.CALL_PHONE", "android.hardware.telephony");
        map.put("android.permission.CALL_PRIVILEGED", "android.hardware.telephony");
        map.put("android.permission.MODIFY_PHONE_STATE", "android.hardware.telephony");
        map.put("android.permission.PROCESS_OUTGOING_CALLS", "android.hardware.telephony");
        map.put("android.permission.READ_SMS", "android.hardware.telephony");
        map.put("android.permission.RECEIVE_SMS", "android.hardware.telephony");
        map.put("android.permission.SEND_SMS", "android.hardware.telephony");
        map.put("android.permission.WRITE_APN_SETTINGS", "android.hardware.telephony");
        map.put("android.permission.READ_PHONE_STATE", "android.hardware.telephony");
        PERMISSION_TO_FEATURE = Collections.unmodifiableMap(map);
    }

    private final Map<String, Boolean> mFeatures = new HashMap<>();
    private final List<Element> mPermissions = new ArrayList<>();

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                SdkConstants.TAG_USES_PERMISSION,
                SdkConstants.TAG_USES_FEATURE
        );
    }

    @Override
    public void beforeCheckFile(com.android.tools.lint.detector.api.Context context) {
        mFeatures.clear();
        mPermissions.clear();
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Element root = context.getDocument().getDocumentElement();
        if (root == null || !SdkConstants.TAG_MANIFEST.equals(root.getTagName())) {
            return;
        }

        String tag = element.getTagName();
        if (SdkConstants.TAG_USES_FEATURE.equals(tag)) {
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if (!name.isEmpty()) {
                String required = element.getAttributeNS(
                        SdkConstants.ANDROID_URI, SdkConstants.ATTR_REQUIRED);
                boolean isRequired = required.isEmpty()
                        || SdkConstants.VALUE_TRUE.equals(required);
                mFeatures.put(name, isRequired);
            }
        } else if (SdkConstants.TAG_USES_PERMISSION.equals(tag)) {
            mPermissions.add(element);
        }
    }

    @Override
    public void afterCheckFile(com.android.tools.lint.detector.api.Context context) {
        if (!(context instanceof XmlContext)) {
            return;
        }
        XmlContext xmlContext = (XmlContext) context;
        Element root = xmlContext.getDocument().getDocumentElement();
        if (root == null || !SdkConstants.TAG_MANIFEST.equals(root.getTagName())) {
            return;
        }
        if (!isTvApplication(root)) {
            return;
        }

        for (Element permission : mPermissions) {
            String name = permission.getAttributeNS(
                    SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            String feature = PERMISSION_TO_FEATURE.get(name);
            if (feature == null) {
                continue;
            }
            Boolean required = mFeatures.get(feature);
            if (required == null || required) {
                String message = String.format(
                        "Permission `%1$s` implies the unsupported TV hardware feature `%2$s`. "
                                + "Consider adding `<uses-feature android:name=\"%2$s\" "
                                + "android:required=\"false\" />`.",
                        name, feature);
                xmlContext.report(ISSUE, xmlContext.getLocation(permission), message);
            }
        }
    }

    private static boolean isTvApplication(Element root) {
        NodeList features = root.getElementsByTagName(SdkConstants.TAG_USES_FEATURE);
        for (int i = 0, n = features.getLength(); i < n; i++) {
            Element element = (Element) features.item(i);
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if (SdkConstants.FEATURE_TELEVISION.equals(name)) {
                String required = element.getAttributeNS(
                        SdkConstants.ANDROID_URI, SdkConstants.ATTR_REQUIRED);
                if (required.isEmpty() || SdkConstants.VALUE_TRUE.equals(required)) {
                    return true;
                }
            }
        }

        NodeList categories = root.getElementsByTagName(SdkConstants.TAG_CATEGORY);
        for (int i = 0, n = categories.getLength(); i < n; i++) {
            Element element = (Element) categories.item(i);
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if (SdkConstants.CATEGORY_LEANBACK_LAUNCHER.equals(name)) {
                return true;
            }
        }

        return false;
    }
}
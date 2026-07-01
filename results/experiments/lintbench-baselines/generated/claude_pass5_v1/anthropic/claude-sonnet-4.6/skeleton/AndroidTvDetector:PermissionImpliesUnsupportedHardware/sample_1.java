package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String TAG_USES_PERMISSION = "uses-permission";
    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String ATTR_NAME = "android:name";
    private static final String ATTR_REQUIRED = "android:required";

    /**
     * Map from permission name to the implied hardware feature it requires.
     * Based on https://developer.android.com/guide/topics/manifest/uses-feature-element.html#permissions
     */
    private static final Map<String, String> PERMISSION_TO_FEATURE = new HashMap<>();

    static {
        PERMISSION_TO_FEATURE.put(
                "android.permission.BLUETOOTH",
                "android.hardware.bluetooth");
        PERMISSION_TO_FEATURE.put(
                "android.permission.BLUETOOTH_ADMIN",
                "android.hardware.bluetooth");
        PERMISSION_TO_FEATURE.put(
                "android.permission.CAMERA",
                "android.hardware.camera");
        PERMISSION_TO_FEATURE.put(
                "android.permission.RECORD_AUDIO",
                "android.hardware.microphone");
        PERMISSION_TO_FEATURE.put(
                "android.permission.ACCESS_WIFI_STATE",
                "android.hardware.wifi");
        PERMISSION_TO_FEATURE.put(
                "android.permission.CHANGE_WIFI_STATE",
                "android.hardware.wifi");
        PERMISSION_TO_FEATURE.put(
                "android.permission.CHANGE_WIFI_MULTICAST_STATE",
                "android.hardware.wifi");
        PERMISSION_TO_FEATURE.put(
                "android.permission.ACCESS_FINE_LOCATION",
                "android.hardware.location.gps");
        PERMISSION_TO_FEATURE.put(
                "android.permission.ACCESS_MOCK_LOCATION",
                "android.hardware.location");
        PERMISSION_TO_FEATURE.put(
                "android.permission.ACCESS_COARSE_LOCATION",
                "android.hardware.location.network");
        PERMISSION_TO_FEATURE.put(
                "android.permission.CALL_PHONE",
                "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put(
                "android.permission.CALL_PRIVILEGED",
                "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put(
                "android.permission.MODIFY_PHONE_STATE",
                "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put(
                "android.permission.PROCESS_OUTGOING_CALLS",
                "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put(
                "android.permission.READ_SMS",
                "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put(
                "android.permission.RECEIVE_SMS",
                "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put(
                "android.permission.RECEIVE_MMS",
                "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put(
                "android.permission.RECEIVE_WAP_PUSH",
                "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put(
                "android.permission.SEND_SMS",
                "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put(
                "android.permission.WRITE_APN_SETTINGS",
                "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put(
                "android.permission.WRITE_SMS",
                "android.hardware.telephony");
    }

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "PermissionImpliesUnsupportedHardware",
                    "Permission Implies Unsupported Hardware",
                    "The `<uses-permission>` element should not require a permission that "
                            + "implies an unsupported TV hardware feature. Google Play assumes "
                            + "that certain hardware related permissions indicate that the "
                            + "underlying hardware features are required by default. To fix "
                            + "the issue, consider declaring the corresponding `uses-feature` "
                            + "element with `required=\"false\"` attribute.",
                    Category.CORRECTNESS,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    /** Set of features that are declared as not required (required="false") */
    private Set<String> mDeclaredNotRequiredFeatures;

    /** Map from permission element to the feature it implies, for permissions found in this file */
    private Map<Element, String> mPermissionToImpliedFeature;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_USES_PERMISSION, TAG_USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mDeclaredNotRequiredFeatures = new HashSet<>();
        mPermissionToImpliedFeature = new HashMap<>();
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mPermissionToImpliedFeature == null || mPermissionToImpliedFeature.isEmpty()) {
            return;
        }

        XmlContext xmlContext = (XmlContext) context;

        for (Map.Entry<Element, String> entry : mPermissionToImpliedFeature.entrySet()) {
            Element permissionElement = entry.getKey();
            String impliedFeature = entry.getValue();

            // If the implied feature is NOT declared as not-required, report an issue
            if (!mDeclaredNotRequiredFeatures.contains(impliedFeature)) {
                String permissionName = getAttributeValue(permissionElement, "android:name");
                if (permissionName == null) {
                    permissionName = getAttributeValue(permissionElement, "name");
                }

                String message =
                        String.format(
                                "Permission `%s` implies that the app requires `%s` hardware. "
                                        + "If the app does not require this hardware, consider "
                                        + "declaring the corresponding `<uses-feature>` element "
                                        + "with `required=\"false\"` attribute.",
                                permissionName,
                                impliedFeature);

                xmlContext.report(
                        ISSUE,
                        permissionElement,
                        xmlContext.getLocation(permissionElement),
                        message);
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();

        if (TAG_USES_PERMISSION.equals(tagName)) {
            handleUsesPermission(element);
        } else if (TAG_USES_FEATURE.equals(tagName)) {
            handleUsesFeature(element);
        }
    }

    private void handleUsesPermission(@NonNull Element element) {
        String permissionName = getAttributeValue(element, "android:name");
        if (permissionName == null) {
            permissionName = getAttributeValue(element, "name");
        }

        if (permissionName != null) {
            String impliedFeature = PERMISSION_TO_FEATURE.get(permissionName);
            if (impliedFeature != null) {
                mPermissionToImpliedFeature.put(element, impliedFeature);
            }
        }
    }

    private void handleUsesFeature(@NonNull Element element) {
        String featureName = getAttributeValue(element, "android:name");
        if (featureName == null) {
            featureName = getAttributeValue(element, "name");
        }

        if (featureName != null) {
            String requiredValue = getAttributeValue(element, "android:required");
            if (requiredValue == null) {
                requiredValue = getAttributeValue(element, "required");
            }

            // If required is explicitly set to "false", add to the not-required set
            if ("false".equals(requiredValue)) {
                mDeclaredNotRequiredFeatures.add(featureName);
            }
        }
    }

    private String getAttributeValue(@NonNull Element element, @NonNull String attributeName) {
        // Try with namespace prefix first
        if (element.hasAttribute(attributeName)) {
            String value = element.getAttribute(attributeName);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }

        // Try iterating attributes for namespace-aware lookup
        org.w3c.dom.NamedNodeMap attributes = element.getAttributes();
        if (attributes != null) {
            for (int i = 0; i < attributes.getLength(); i++) {
                org.w3c.dom.Node attr = attributes.item(i);
                String localName = attr.getLocalName();
                String nodeName = attr.getNodeName();

                // Match by local name (e.g., "name", "required")
                String simpleAttrName = attributeName.contains(":")
                        ? attributeName.substring(attributeName.indexOf(':') + 1)
                        : attributeName;

                if (simpleAttrName.equals(localName) || attributeName.equals(nodeName)) {
                    String value = attr.getNodeValue();
                    if (value != null && !value.isEmpty()) {
                        return value;
                    }
                }
            }
        }

        return null;
    }
}
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

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android";
    private static final String TAG_USES_PERMISSION = "uses-permission";
    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_REQUIRED = "required";
    private static final String VALUE_FALSE = "false";

    private static final Map<String, String> PERMISSION_TO_FEATURE = new HashMap<>();
    static {
        PERMISSION_TO_FEATURE.put("android.permission.BLUETOOTH", "android.hardware.bluetooth");
        PERMISSION_TO_FEATURE.put("android.permission.BLUETOOTH_ADMIN", "android.hardware.bluetooth");
        PERMISSION_TO_FEATURE.put("android.permission.CAMERA", "android.hardware.camera");
        PERMISSION_TO_FEATURE.put("android.permission.ACCESS_FINE_LOCATION", "android.hardware.location.gps");
        PERMISSION_TO_FEATURE.put("android.permission.ACCESS_COARSE_LOCATION", "android.hardware.location.network");
        PERMISSION_TO_FEATURE.put("android.permission.NFC", "android.hardware.nfc");
        PERMISSION_TO_FEATURE.put("android.permission.CALL_PHONE", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.SEND_SMS", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.RECEIVE_SMS", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.READ_PHONE_STATE", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.MODIFY_PHONE_STATE", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.PROCESS_OUTGOING_CALLS", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.READ_SMS", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.WRITE_SMS", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.RECEIVE_WAP_PUSH", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.RECEIVE_MMS", "android.hardware.telephony");
    }

    public static final Issue ISSUE = Issue.create(
            "PermissionImpliesUnsupportedHardware",
            "Permission Implies Unsupported Hardware",
            "The `<uses-permission>` element should not require a permission that implies an unsupported TV hardware feature. " +
            "Google Play assumes that certain hardware related permissions indicate that the underlying hardware features are required by default. " +
            "To fix the issue, consider declaring the corresponding `uses-feature` element with `required=\"false\" attribute.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private Set<String> optionalFeatures;

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_USES_PERMISSION);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        optionalFeatures = new HashSet<>();
        if (context instanceof XmlContext) {
            Document document = ((XmlContext) context).document;
            if (document != null) {
                NodeList features = document.getElementsByTagName(TAG_USES_FEATURE);
                for (int i = 0; i < features.getLength(); i++) {
                    Node node = features.item(i);
                    if (node instanceof Element) {
                        Element element = (Element) node;
                        String required = element.getAttributeNS(ANDROID_NAMESPACE, ATTR_REQUIRED);
                        if (VALUE_FALSE.equals(required)) {
                            String name = element.getAttributeNS(ANDROID_NAMESPACE, ATTR_NAME);
                            if (!name.isEmpty()) {
                                optionalFeatures.add(name);
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        optionalFeatures = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String permission = element.getAttributeNS(ANDROID_NAMESPACE, ATTR_NAME);
        String impliedFeature = PERMISSION_TO_FEATURE.get(permission);
        if (impliedFeature != null && !optionalFeatures.contains(impliedFeature)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Permission `" + permission + "` implies unsupported TV hardware feature `" + impliedFeature + "`. " +
                    "Consider declaring `<uses-feature android:name=\"" + impliedFeature + "\" android:required=\"false\" />`."
            );
        }
    }
}
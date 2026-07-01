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
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "PermissionImpliesUnsupportedHardware",
                    "Permission Implies Unsupported Hardware",
                    "The `<uses-permission>` element should not require a permission that implies an unsupported TV hardware feature. " +
                    "Google Play assumes that certain hardware related permissions indicate that the underlying hardware features are required by default. " +
                    "To fix the issue, consider declaring the corresponding `uses-feature` element with `required=\"false\"` attribute.",
                    Category.CORRECTNESS,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final Map<String, String> PERMISSION_TO_FEATURE = new HashMap<>();
    static {
        PERMISSION_TO_FEATURE.put("android.permission.CAMERA", "android.hardware.camera");
        PERMISSION_TO_FEATURE.put("android.permission.ACCESS_FINE_LOCATION", "android.hardware.location.gps");
        PERMISSION_TO_FEATURE.put("android.permission.NFC", "android.hardware.nfc");
        PERMISSION_TO_FEATURE.put("android.permission.CALL_PHONE", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.SEND_SMS", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.RECEIVE_SMS", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.READ_PHONE_STATE", "android.hardware.telephony");
        PERMISSION_TO_FEATURE.put("android.permission.PROCESS_OUTGOING_CALLS", "android.hardware.telephony");
    }

    private Set<String> mOptionalFeatures;

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("uses-permission");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mOptionalFeatures = new HashSet<>();
        if (!(context instanceof XmlContext)) {
            return;
        }
        XmlContext xmlContext = (XmlContext) context;
        if (xmlContext.document == null) {
            return;
        }
        Element root = xmlContext.document.getDocumentElement();
        if (root == null) {
            return;
        }

        NodeList features = root.getElementsByTagName("uses-feature");
        for (int i = 0; i < features.getLength(); i++) {
            Element feature = (Element) features.item(i);
            String required = feature.getAttribute("android:required");
            if ("false".equals(required)) {
                String name = feature.getAttribute("android:name");
                if (name != null && !name.isEmpty()) {
                    mOptionalFeatures.add(name);
                }
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        mOptionalFeatures = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String permission = element.getAttribute("android:name");
        if (permission == null || permission.isEmpty()) {
            return;
        }

        String impliedFeature = PERMISSION_TO_FEATURE.get(permission);
        if (impliedFeature != null && !mOptionalFeatures.contains(impliedFeature)) {
            String message = String.format(
                    "Permission implies unsupported TV hardware feature. " +
                    "Consider declaring <uses-feature android:name=\"%s\" android:required=\"false\" />.",
                    impliedFeature);
            context.report(ISSUE, element, context.getLocation(element), message);
        }
    }
}
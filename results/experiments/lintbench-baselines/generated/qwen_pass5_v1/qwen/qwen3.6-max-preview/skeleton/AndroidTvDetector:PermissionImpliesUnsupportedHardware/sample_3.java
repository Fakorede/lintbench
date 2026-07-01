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
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

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

    private Set<String> mOptionalFeatures;

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("uses-permission");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mOptionalFeatures = new HashSet<>();
        if (context instanceof XmlContext) {
            Document document = ((XmlContext) context).document;
            if (document != null) {
                NodeList features = document.getElementsByTagName("uses-feature");
                for (int i = 0; i < features.getLength(); i++) {
                    Element feature = (Element) features.item(i);
                    String required = feature.getAttributeNS(ANDROID_URI, "required");
                    if ("false".equals(required)) {
                        String name = feature.getAttributeNS(ANDROID_URI, "name");
                        if (name != null && !name.isEmpty()) {
                            mOptionalFeatures.add(name);
                        }
                    }
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
        String permission = element.getAttributeNS(ANDROID_URI, "name");
        String impliedFeature = getImpliedFeature(permission);
        if (impliedFeature != null && !mOptionalFeatures.contains(impliedFeature)) {
            String message = String.format(
                    "Permission %s implies hardware feature %s, which may not be supported on Android TV. " +
                    "Consider adding `<uses-feature android:name=\"%s\" android:required=\"false\" />` to the manifest.",
                    permission, impliedFeature, impliedFeature);
            context.report(ISSUE, element, context.getLocation(element), message);
        }
    }

    private static String getImpliedFeature(String permission) {
        if (permission == null) {
            return null;
        }
        switch (permission) {
            case "android.permission.CAMERA":
                return "android.hardware.camera";
            case "android.permission.ACCESS_COARSE_LOCATION":
                return "android.hardware.location.network";
            case "android.permission.ACCESS_FINE_LOCATION":
                return "android.hardware.location.gps";
            case "android.permission.BLUETOOTH":
            case "android.permission.BLUETOOTH_ADMIN":
            case "android.permission.BLUETOOTH_CONNECT":
            case "android.permission.BLUETOOTH_SCAN":
            case "android.permission.BLUETOOTH_ADVERTISE":
                return "android.hardware.bluetooth";
            case "android.permission.NFC":
                return "android.hardware.nfc";
            case "android.permission.RECORD_AUDIO":
                return "android.hardware.microphone";
            case "android.permission.CALL_PHONE":
            case "android.permission.CALL_PRIVILEGED":
            case "android.permission.MODIFY_PHONE_STATE":
            case "android.permission.PROCESS_OUTGOING_CALLS":
            case "android.permission.READ_SMS":
            case "android.permission.RECEIVE_SMS":
            case "android.permission.RECEIVE_MMS":
            case "android.permission.RECEIVE_WAP_PUSH":
            case "android.permission.SEND_SMS":
            case "android.permission.WRITE_APN_SETTINGS":
                return "android.hardware.telephony";
            default:
                return null;
        }
    }
}
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements XmlScanner {

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

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static class PermissionInfo {
        final Element element;
        final String name;

        PermissionInfo(Element element, String name) {
            this.element = element;
            this.name = name;
        }
    }

    private final List<PermissionInfo> mPermissions = new ArrayList<>();
    private final Map<String, Boolean> mFeatures = new HashMap<>();
    private boolean mHasLeanbackLauncher = false;
    private boolean mHasLeanbackFeature = false;

    @Override
    public Collection<String> getApplicableElements() {
        return java.util.Arrays.asList("uses-permission", "uses-feature", "category");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mPermissions.clear();
        mFeatures.clear();
        mHasLeanbackLauncher = false;
        mHasLeanbackFeature = false;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if ("uses-permission".equals(tagName)) {
            String name = element.getAttributeNS(ANDROID_URI, "name");
            if (!name.isEmpty()) {
                mPermissions.add(new PermissionInfo(element, name));
            }
        } else if ("uses-feature".equals(tagName)) {
            String name = element.getAttributeNS(ANDROID_URI, "name");
            if (!name.isEmpty()) {
                String requiredStr = element.getAttributeNS(ANDROID_URI, "required");
                boolean required = requiredStr.isEmpty() || Boolean.parseBoolean(requiredStr);
                mFeatures.put(name, required);
                if ("android.software.leanback".equals(name)) {
                    mHasLeanbackFeature = true;
                }
            }
        } else if ("category".equals(tagName)) {
            String name = element.getAttributeNS(ANDROID_URI, "name");
            if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                mHasLeanbackLauncher = true;
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!(context instanceof XmlContext)) {
            return;
        }
        XmlContext xmlContext = (XmlContext) context;
        boolean isTvApp = mHasLeanbackLauncher || mHasLeanbackFeature;
        if (!isTvApp) {
            return;
        }

        for (PermissionInfo info : mPermissions) {
            String impliedFeature = getImpliedFeature(info.name);
            if (impliedFeature != null) {
                Boolean required = mFeatures.get(impliedFeature);
                if (required == null || required) {
                    String message = String.format(
                            "Permission `%1$s` implies `%2$s` hardware, which is "
                                    + "not supported on TV. Consider adding `<uses-feature "
                                    + "android:name=\"%2$s\" android:required=\"false\" />` "
                                    + "to your manifest.",
                            info.name, impliedFeature);
                    xmlContext.report(ISSUE, info.element, xmlContext.getLocation(info.element), message);
                }
            }
        }
    }

    private static String getImpliedFeature(String permission) {
        switch (permission) {
            case "android.permission.CAMERA":
                return "android.hardware.camera";
            case "android.permission.RECORD_AUDIO":
                return "android.hardware.microphone";
            case "android.permission.ACCESS_FINE_LOCATION":
                return "android.hardware.location.gps";
            case "android.permission.ACCESS_COARSE_LOCATION":
                return "android.hardware.location.network";
            case "android.permission.CALL_PHONE":
            case "android.permission.SEND_SMS":
            case "android.permission.RECEIVE_SMS":
            case "android.permission.RECEIVE_MMS":
            case "android.permission.RECEIVE_WAP_PUSH":
            case "android.permission.READ_SMS":
            case "android.permission.WRITE_SMS":
                return "android.hardware.telephony";
            default:
                return null;
        }
    }
}
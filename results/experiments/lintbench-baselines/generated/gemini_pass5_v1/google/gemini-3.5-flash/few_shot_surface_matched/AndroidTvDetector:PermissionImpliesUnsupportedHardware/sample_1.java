package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AndroidTvDetector extends Detector implements XmlScanner {

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
                    5,
                    Severity.WARNING,
                    new Implementation(
                            AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private boolean mIsTvApp = false;
    private final Map<String, Boolean> mFeatures = new HashMap<>();
    private final List<Element> mPermissions = new ArrayList<>();

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("uses-permission", "uses-feature", "category");
    }

    @Override
    public void beforeCheckFile(@NonNull XmlContext context) {
        mIsTvApp = false;
        mFeatures.clear();
        mPermissions.clear();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if ("uses-permission".equals(tagName)) {
            mPermissions.add(element);
        } else if ("uses-feature".equals(tagName)) {
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if ("android.software.leanback".equals(name)) {
                mIsTvApp = true;
            }
            String requiredStr = element.getAttributeNS("http://schemas.android.com/apk/res/android", "required");
            boolean required = requiredStr.isEmpty() || Boolean.parseBoolean(requiredStr);
            mFeatures.put(name, required);
        } else if ("category".equals(tagName)) {
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                mIsTvApp = true;
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull XmlContext context) {
        if (!mIsTvApp) {
            return;
        }

        for (Element permissionElement : mPermissions) {
            String permissionName = permissionElement.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if (permissionName == null || permissionName.isEmpty()) {
                continue;
            }

            String impliedFeature = getImpliedHardwareFeature(permissionName);
            if (impliedFeature != null) {
                Boolean required = mFeatures.get(impliedFeature);
                if (required == null || required) {
                    context.report(
                            ISSUE,
                            permissionElement,
                            context.getLocation(permissionElement),
                            String.format(
                                    "Permission `%1$s` implies `%2$s` hardware, which is "
                                            + "not available on TV. Consider adding `<uses-feature "
                                            + "android:name=\"%2$s\" android:required=\"false\" />` "
                                            + "to the manifest.",
                                    permissionName,
                                    impliedFeature));
                }
            }
        }
    }

    private static String getImpliedHardwareFeature(String permission) {
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
            case "android.permission.PROCESS_OUTGOING_CALLS":
            case "android.permission.READ_SMS":
            case "android.permission.WRITE_SMS":
                return "android.hardware.telephony";
            default:
                return null;
        }
    }
}
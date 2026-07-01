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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
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
                    IMPLEMENTATION);

    private static final Map<String, List<String>> IMPLIED_FEATURES = new HashMap<>();

    static {
        IMPLIED_FEATURES.put("android.permission.CAMERA", Arrays.asList("android.hardware.camera"));
        IMPLIED_FEATURES.put("android.permission.RECORD_AUDIO", Arrays.asList("android.hardware.microphone"));
        IMPLIED_FEATURES.put("android.permission.ACCESS_FINE_LOCATION", Arrays.asList("android.hardware.location", "android.hardware.location.gps"));
        IMPLIED_FEATURES.put("android.permission.ACCESS_COARSE_LOCATION", Arrays.asList("android.hardware.location", "android.hardware.location.network"));
        IMPLIED_FEATURES.put("android.permission.NFC", Arrays.asList("android.hardware.nfc"));

        List<String> telephony = Arrays.asList("android.hardware.telephony");
        IMPLIED_FEATURES.put("android.permission.SEND_SMS", telephony);
        IMPLIED_FEATURES.put("android.permission.RECEIVE_SMS", telephony);
        IMPLIED_FEATURES.put("android.permission.READ_SMS", telephony);
        IMPLIED_FEATURES.put("android.permission.RECEIVE_WAP_PUSH", telephony);
        IMPLIED_FEATURES.put("android.permission.RECEIVE_MMS", telephony);
        IMPLIED_FEATURES.put("android.permission.CALL_PHONE", telephony);
        IMPLIED_FEATURES.put("android.permission.PROCESS_OUTGOING_CALLS", telephony);
        IMPLIED_FEATURES.put("android.permission.READ_PHONE_STATE", telephony);
    }

    private boolean mIsTvApp = false;
    private final List<PermissionInfo> mPermissions = new ArrayList<>();
    private final Map<String, Boolean> mFeatureRequiredMap = new HashMap<>();

    private static class PermissionInfo {
        final String name;
        final Element element;

        PermissionInfo(String name, Element element) {
            this.name = name;
            this.element = element;
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("uses-permission", "uses-feature", "category");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIsTvApp = false;
        mPermissions.clear();
        mFeatureRequiredMap.clear();
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!(context instanceof XmlContext)) {
            return;
        }
        XmlContext xmlContext = (XmlContext) context;
        if (!mIsTvApp) {
            return;
        }

        for (PermissionInfo permissionInfo : mPermissions) {
            List<String> impliedFeatures = IMPLIED_FEATURES.get(permissionInfo.name);
            if (impliedFeatures != null) {
                for (String feature : impliedFeatures) {
                    Boolean required = mFeatureRequiredMap.get(feature);
                    if (required == null || required) {
                        String message = String.format(
                                "Permission `%1$s` implies `%2$s` hardware feature which is not supported on TV. " +
                                "Consider declaring `%2$s` with `android:required=\"false\"`.",
                                permissionInfo.name, feature);
                        xmlContext.report(ISSUE, permissionInfo.element, xmlContext.getLocation(permissionInfo.element), message);
                    }
                }
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if ("uses-permission".equals(tagName)) {
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if (!name.isEmpty()) {
                mPermissions.add(new PermissionInfo(name, element));
            }
        } else if ("uses-feature".equals(tagName)) {
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if (!name.isEmpty()) {
                String requiredStr = element.getAttributeNS("http://schemas.android.com/apk/res/android", "required");
                boolean required = requiredStr.isEmpty() || Boolean.parseBoolean(requiredStr);
                mFeatureRequiredMap.put(name, required);
                if ("android.software.leanback".equals(name)) {
                    mIsTvApp = true;
                }
            }
        } else if ("category".equals(tagName)) {
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                mIsTvApp = true;
            }
        }
    }
}
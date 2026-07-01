package com.android.tools.lint.checks;

import com.android.SdkConstants;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Element;

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
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(
                    AndroidTvDetector.class,
                    Scope.MANIFEST_SCOPE
            )
    );

    private static final Map<String, String[]> IMPLIES = new HashMap<>();

    static {
        IMPLIES.put("android.permission.CAMERA", new String[]{
                "android.hardware.camera"
        });
        IMPLIES.put("android.permission.RECORD_AUDIO", new String[]{
                "android.hardware.microphone"
        });
        IMPLIES.put("android.permission.ACCESS_FINE_LOCATION", new String[]{
                "android.hardware.location.gps",
                "android.hardware.location"
        });
        IMPLIES.put("android.permission.ACCESS_COARSE_LOCATION", new String[]{
                "android.hardware.location.network",
                "android.hardware.location"
        });
        String[] telephonyImplied = new String[]{"android.hardware.telephony"};
        IMPLIES.put("android.permission.CALL_PHONE", telephonyImplied);
        IMPLIES.put("android.permission.SEND_SMS", telephonyImplied);
        IMPLIES.put("android.permission.RECEIVE_SMS", telephonyImplied);
        IMPLIES.put("android.permission.RECEIVE_MMS", telephonyImplied);
        IMPLIES.put("android.permission.RECEIVE_WAP_PUSH", telephonyImplied);
        IMPLIES.put("android.permission.READ_SMS", telephonyImplied);
        IMPLIES.put("android.permission.WRITE_SMS", telephonyImplied);
    }

    private boolean mIsTvApp = false;
    private final List<Element> mPermissions = new ArrayList<>();
    private final Set<String> mFeaturesNotRequired = new HashSet<>();

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIsTvApp = false;
        mPermissions.clear();
        mFeaturesNotRequired.clear();
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                "uses-permission",
                "uses-feature",
                "category"
        );
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if ("uses-permission".equals(tagName)) {
            mPermissions.add(element);
        } else if ("uses-feature".equals(tagName)) {
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI, "name");
            String requiredStr = element.getAttributeNS(SdkConstants.ANDROID_URI, "required");
            boolean required = requiredStr.isEmpty() || Boolean.parseBoolean(requiredStr);
            if (!required) {
                mFeaturesNotRequired.add(name);
            }
            if ("android.software.leanback".equals(name)) {
                mIsTvApp = true;
            }
        } else if ("category".equals(tagName)) {
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI, "name");
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
            String permissionName = permissionElement.getAttributeNS(SdkConstants.ANDROID_URI, "name");
            if (permissionName == null || permissionName.isEmpty()) {
                continue;
            }

            String[] impliedFeatures = IMPLIES.get(permissionName);
            if (impliedFeatures != null) {
                for (String feature : impliedFeatures) {
                    if (!mFeaturesNotRequired.contains(feature)) {
                        String message = String.format(
                                "Permission `%1$s` implies `%2$s` hardware but it is not declared with `required=\"false\"` in the manifest",
                                permissionName, feature);
                        context.report(ISSUE, permissionElement, context.getLocation(permissionElement), message);
                    }
                }
            }
        }
    }
}
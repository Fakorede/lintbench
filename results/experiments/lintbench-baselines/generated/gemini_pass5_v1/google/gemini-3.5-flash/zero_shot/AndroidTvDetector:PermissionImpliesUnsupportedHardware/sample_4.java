package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

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
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE)
    );

    private static final Map<String, List<String>> IMPLIED_FEATURES = new HashMap<>();
    static {
        IMPLIED_FEATURES.put("android.permission.CAMERA", Arrays.asList("android.hardware.camera", "android.hardware.camera.autofocus"));
        IMPLIED_FEATURES.put("android.permission.RECORD_AUDIO", Arrays.asList("android.hardware.microphone"));
        IMPLIED_FEATURES.put("android.permission.ACCESS_FINE_LOCATION", Arrays.asList("android.hardware.location", "android.hardware.location.gps"));
        IMPLIED_FEATURES.put("android.permission.ACCESS_COARSE_LOCATION", Arrays.asList("android.hardware.location", "android.hardware.location.network"));
        IMPLIED_FEATURES.put("android.permission.CALL_PHONE", Arrays.asList("android.hardware.telephony"));
        IMPLIED_FEATURES.put("android.permission.SEND_SMS", Arrays.asList("android.hardware.telephony"));
        IMPLIED_FEATURES.put("android.permission.RECEIVE_SMS", Arrays.asList("android.hardware.telephony"));
        IMPLIED_FEATURES.put("android.permission.RECEIVE_MMS", Arrays.asList("android.hardware.telephony"));
        IMPLIED_FEATURES.put("android.permission.RECEIVE_WAP_PUSH", Arrays.asList("android.hardware.telephony"));
        IMPLIED_FEATURES.put("android.permission.READ_SMS", Arrays.asList("android.hardware.telephony"));
        IMPLIED_FEATURES.put("android.permission.WRITE_SMS", Arrays.asList("android.hardware.telephony"));
    }

    private boolean mIsTvApp = false;
    private final List<PermissionDeclaration> mPermissions = new ArrayList<>();
    private final Map<String, Boolean> mFeatures = new HashMap<>();

    private static class PermissionDeclaration {
        final String name;
        final Element element;
        final Location location;

        PermissionDeclaration(String name, Element element, Location location) {
            this.name = name;
            this.element = element;
            this.location = location;
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("uses-permission", "uses-feature", "category");
    }

    @Override
    public void beforeCheckFile(XmlContext context) {
        mIsTvApp = false;
        mPermissions.clear();
        mFeatures.clear();
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tagName = element.getTagName();
        if ("uses-permission".equals(tagName)) {
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if (name != null && !name.isEmpty()) {
                mPermissions.add(new PermissionDeclaration(name, element, context.getLocation(element)));
            }
        } else if ("uses-feature".equals(tagName)) {
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if (name != null && !name.isEmpty()) {
                String requiredStr = element.getAttributeNS(SdkConstants.ANDROID_URI, "required");
                boolean required = requiredStr.isEmpty() || Boolean.parseBoolean(requiredStr);
                mFeatures.put(name, required);
                if ("android.software.leanback".equals(name)) {
                    mIsTvApp = true;
                }
            }
        } else if ("category".equals(tagName)) {
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                mIsTvApp = true;
            }
        }
    }

    @Override
    public void afterCheckFile(XmlContext context) {
        if (!mIsTvApp) {
            return;
        }

        for (PermissionDeclaration perm : mPermissions) {
            List<String> implied = IMPLIED_FEATURES.get(perm.name);
            if (implied != null) {
                for (String feature : implied) {
                    Boolean required = mFeatures.get(feature);
                    if (required == null || required) {
                        String message = String.format(
                                "Permission `%1$s` implies `%2$s` hardware but it is not declared as optional " +
                                "with `<uses-feature android:name=\"%2$s\" android:required=\"false\" />`",
                                perm.name, feature);
                        context.report(ISSUE, perm.element, perm.location, message);
                    }
                }
            }
        }
    }
}
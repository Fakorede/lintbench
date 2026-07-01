package com.android.tools.lint.checks;

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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
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
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private boolean mIsTvApp = false;
    private final Set<String> mFeaturesNotRequired = new HashSet<>();
    private final List<Element> mPermissions = new ArrayList<>();

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("uses-permission", "uses-feature", "category");
    }

    @Override
    public void beforeCheckFile(XmlContext context) {
        mIsTvApp = false;
        mFeaturesNotRequired.clear();
        mPermissions.clear();
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tagName = element.getTagName();
        if ("uses-feature".equals(tagName)) {
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if ("android.software.leanback".equals(name)) {
                mIsTvApp = true;
            }
            String required = element.getAttributeNS("http://schemas.android.com/apk/res/android", "required");
            if ("false".equals(required)) {
                mFeaturesNotRequired.add(name);
            }
        } else if ("category".equals(tagName)) {
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                mIsTvApp = true;
            }
        } else if ("uses-permission".equals(tagName)) {
            mPermissions.add(element);
        }
    }

    @Override
    public void afterCheckFile(XmlContext context) {
        if (!mIsTvApp) {
            return;
        }
        for (Element permissionElement : mPermissions) {
            String permission = permissionElement.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if (permission == null || permission.isEmpty()) {
                continue;
            }
            String impliedFeature = getImpliedFeature(permission);
            if (impliedFeature != null) {
                if (!mFeaturesNotRequired.contains(impliedFeature)) {
                    context.report(
                            ISSUE,
                            permissionElement,
                            context.getLocation(permissionElement),
                            String.format("Permission `%1$s` implies `%2$s` hardware but it is not declared as optional with `<uses-feature android:name=\"%2$s\" android:required=\"false\" />`",
                                    permission, impliedFeature));
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
            case "android.permission.SEND_RESPOND_VIA_MESSAGE":
                return "android.hardware.telephony";
            default:
                return null;
        }
    }
}
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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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

    private final Map<String, Element> mPermissions = new HashMap<>();
    private final Set<String> mFeaturesNotRequired = new HashSet<>();
    private boolean mIsTvApp = false;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("uses-permission", "uses-feature", "category");
    }

    @Override
    public void beforeCheckFile(XmlContext context) {
        mPermissions.clear();
        mFeaturesNotRequired.clear();
        mIsTvApp = false;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tagName = element.getTagName();
        String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
        if (name == null || name.isEmpty()) {
            return;
        }

        if ("uses-permission".equals(tagName)) {
            mPermissions.put(name, element);
        } else if ("uses-feature".equals(tagName)) {
            if ("android.software.leanback".equals(name) || "android.hardware.type.television".equals(name)) {
                mIsTvApp = true;
            }
            String required = element.getAttributeNS("http://schemas.android.com/apk/res/android", "required");
            if ("false".equals(required)) {
                mFeaturesNotRequired.add(name);
            }
        } else if ("category".equals(tagName)) {
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

        for (Map.Entry<String, Element> entry : mPermissions.entrySet()) {
            String permission = entry.getKey();
            String impliedFeature = getImpliedFeature(permission);
            if (impliedFeature != null && !mFeaturesNotRequired.contains(impliedFeature)) {
                Element element = entry.getValue();
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        String.format(
                                "Permission `%1$s` implies `%2$s` hardware, which is "
                                        + "not supported on TV. Consider declaring "
                                        + "`<uses-feature android:name=\"%2$s\" android:required=\"false\" />` "
                                        + "to make your app available on Google Play for TV.",
                                permission, impliedFeature));
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
                return "android.hardware.location";
            case "android.permission.CALL_PHONE":
            case "android.permission.SEND_SMS":
            case "android.permission.RECEIVE_SMS":
            case "android.permission.RECEIVE_MMS":
            case "android.permission.RECEIVE_WAP_PUSH":
                return "android.hardware.telephony";
            default:
                return null;
        }
    }
}
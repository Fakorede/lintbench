package com.android.tools.lint.checks;

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

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_REQUIRED = "required";
    private static final String VALUE_FALSE = "false";
    private static final String TAG_USES_PERMISSION = "uses-permission";
    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String TAG_CATEGORY = "category";

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

    private boolean mIsTvApp;
    private final List<Element> mPermissions = new ArrayList<>();
    private final Map<String, Boolean> mFeatures = new HashMap<>();

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_USES_PERMISSION, TAG_USES_FEATURE, TAG_CATEGORY);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIsTvApp = false;
        mPermissions.clear();
        mFeatures.clear();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if (TAG_USES_PERMISSION.equals(tagName)) {
            mPermissions.add(element);
        } else if (TAG_USES_FEATURE.equals(tagName)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (!name.isEmpty()) {
                String requiredStr = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
                boolean required = requiredStr.isEmpty() || !VALUE_FALSE.equals(requiredStr);
                mFeatures.put(name, required);
                if ("android.software.leanback".equals(name)) {
                    mIsTvApp = true;
                }
            }
        } else if (TAG_CATEGORY.equals(tagName)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                mIsTvApp = true;
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!mIsTvApp) {
            return;
        }
        XmlContext xmlContext = (XmlContext) context;
        for (Element permissionElement : mPermissions) {
            String permissionName = permissionElement.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (permissionName.isEmpty()) {
                continue;
            }
            String impliedFeature = getImpliedFeature(permissionName);
            if (impliedFeature != null) {
                Boolean required = mFeatures.get(impliedFeature);
                if (required == null || required) {
                    String message = String.format(
                            "Permission `%1$s` implies `%2$s` hardware, which is not "
                                    + "supported on TV. Add `<uses-feature android:name=\"%2$s\" "
                                    + "android:required=\"false\" />` to indicate that this "
                                    + "feature is not required.",
                            permissionName, impliedFeature);
                    xmlContext.report(ISSUE, permissionElement, xmlContext.getLocation(permissionElement), message);
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
            case "android.permission.ACCESS_COARSE_LOCATION":
                return "android.hardware.location";
            case "android.permission.SEND_SMS":
            case "android.permission.RECEIVE_SMS":
            case "android.permission.READ_SMS":
            case "android.permission.RECEIVE_WAP_PUSH":
            case "android.permission.RECEIVE_MMS":
            case "android.permission.CALL_PHONE":
            case "android.permission.PROCESS_OUTGOING_CALLS":
            case "android.permission.READ_PHONE_STATE":
                return "android.hardware.telephony";
            case "android.permission.BLUETOOTH":
            case "android.permission.BLUETOOTH_ADMIN":
                return "android.hardware.bluetooth";
            default:
                return null;
        }
    }
}
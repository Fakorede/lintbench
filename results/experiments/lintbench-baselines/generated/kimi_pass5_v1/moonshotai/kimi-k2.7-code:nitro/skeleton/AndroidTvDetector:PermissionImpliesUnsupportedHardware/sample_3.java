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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String TAG_USES_PERMISSION = "uses-permission";
    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_REQUIRED = "required";
    private static final String FEATURE_LEANBACK = "android.software.leanback";
    private static final String CATEGORY_LEANBACK_LAUNCHER = "android.intent.category.LEANBACK_LAUNCHER";

    private static final Map<String, List<String>> PERMISSION_TO_FEATURES;

    static {
        Map<String, List<String>> map = new HashMap<>();
        List<String> telephony = Collections.singletonList("android.hardware.telephony");
        String[] telephonyPermissions = {
                "CALL_PHONE",
                "CALL_PRIVILEGED",
                "MODIFY_PHONE_STATE",
                "PROCESS_OUTGOING_CALLS",
                "READ_PHONE_STATE",
                "READ_SMS",
                "RECEIVE_SMS",
                "RECEIVE_MMS",
                "RECEIVE_WAP_PUSH",
                "SEND_SMS",
                "WRITE_SMS"
        };
        for (String permission : telephonyPermissions) {
            map.put("android.permission." + permission, telephony);
        }

        map.put("android.permission.CAMERA",
                Arrays.asList("android.hardware.camera", "android.hardware.camera.autofocus"));
        map.put("android.permission.ACCESS_FINE_LOCATION",
                Arrays.asList("android.hardware.location", "android.hardware.location.gps"));
        map.put("android.permission.ACCESS_COARSE_LOCATION",
                Collections.singletonList("android.hardware.location"));

        PERMISSION_TO_FEATURES = Collections.unmodifiableMap(map);
    }

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "PermissionImpliesUnsupportedHardware",
                    "Permission Implies Unsupported Hardware",
                    "The `<uses-permission>` element should not require a permission that implies an "
                            + "unsupported TV hardware feature. Google Play assumes that certain hardware "
                            + "related permissions indicate that the underlying hardware features are "
                            + "required by default. To fix the issue, consider declaring the corresponding "
                            + "`<uses-feature>` element with `android:required=\"false\"`.",
                    Category.CORRECTNESS,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private final Map<Element, String> mPermissions = new HashMap<>();
    private final Set<String> mOptionalFeatures = new HashSet<>();
    private boolean mIsTvApp;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_USES_PERMISSION, TAG_USES_FEATURE, "category");
    }

    @Override
    public void beforeCheckFile(Context context) {
        mPermissions.clear();
        mOptionalFeatures.clear();
        mIsTvApp = false;
    }

    @Override
    public void afterCheckFile(Context context) {
        if (!mIsTvApp || !(context instanceof XmlContext) || mPermissions.isEmpty()) {
            return;
        }

        XmlContext xmlContext = (XmlContext) context;
        for (Map.Entry<Element, String> entry : mPermissions.entrySet()) {
            Element element = entry.getKey();
            String permission = entry.getValue();
            List<String> features = PERMISSION_TO_FEATURES.get(permission);
            if (features == null) {
                continue;
            }

            for (String feature : features) {
                if (mOptionalFeatures.contains(feature)) {
                    continue;
                }

                String message = String.format(
                        "Permission `%1$s` implies feature `%2$s`, which is not supported on Android TV. "
                                + "Consider adding `<uses-feature android:name=\"%2$s\" android:required=\"false\" />`.",
                        permission, feature);
                xmlContext.report(ISSUE, element, xmlContext.getLocation(element), message);
            }
        }
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();
        if (TAG_USES_PERMISSION.equals(tag)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (!name.isEmpty()) {
                mPermissions.put(element, name);
            }
        } else if (TAG_USES_FEATURE.equals(tag)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (!name.isEmpty()) {
                if ("false".equals(element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED))) {
                    mOptionalFeatures.add(name);
                }
                if (FEATURE_LEANBACK.equals(name)) {
                    mIsTvApp = true;
                }
            }
        } else if ("category".equals(tag)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (CATEGORY_LEANBACK_LAUNCHER.equals(name)) {
                mIsTvApp = true;
            }
        }
    }
}
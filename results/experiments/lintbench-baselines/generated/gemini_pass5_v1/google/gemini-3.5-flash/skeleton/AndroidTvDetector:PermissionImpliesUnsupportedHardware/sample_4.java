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
import java.util.Collection;
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

    private static final java.util.Map<String, String[]> IMPLIED_FEATURES = new java.util.HashMap<>();
    static {
        IMPLIED_FEATURES.put("android.permission.CAMERA", new String[] {
                "android.hardware.camera",
                "android.hardware.camera.autofocus"
        });
        IMPLIED_FEATURES.put("android.permission.RECORD_AUDIO", new String[] {
                "android.hardware.microphone"
        });
        IMPLIED_FEATURES.put("android.permission.ACCESS_FINE_LOCATION", new String[] {
                "android.hardware.location",
                "android.hardware.location.gps"
        });
        IMPLIED_FEATURES.put("android.permission.ACCESS_COARSE_LOCATION", new String[] {
                "android.hardware.location",
                "android.hardware.location.network"
        });
        IMPLIED_FEATURES.put("android.permission.SEND_SMS", new String[] {
                "android.hardware.telephony"
        });
        IMPLIED_FEATURES.put("android.permission.RECEIVE_SMS", new String[] {
                "android.hardware.telephony"
        });
        IMPLIED_FEATURES.put("android.permission.READ_SMS", new String[] {
                "android.hardware.telephony"
        });
        IMPLIED_FEATURES.put("android.permission.WRITE_SMS", new String[] {
                "android.hardware.telephony"
        });
        IMPLIED_FEATURES.put("android.permission.RECEIVE_MMS", new String[] {
                "android.hardware.telephony"
        });
        IMPLIED_FEATURES.put("android.permission.RECEIVE_WAP_PUSH", new String[] {
                "android.hardware.telephony"
        });
        IMPLIED_FEATURES.put("android.permission.CALL_PHONE", new String[] {
                "android.hardware.telephony"
        });
        IMPLIED_FEATURES.put("android.permission.PROCESS_OUTGOING_CALLS", new String[] {
                "android.hardware.telephony"
        });
        IMPLIED_FEATURES.put("android.permission.READ_PHONE_STATE", new String[] {
                "android.hardware.telephony"
        });
        IMPLIED_FEATURES.put("android.permission.MODIFY_PHONE_STATE", new String[] {
                "android.hardware.telephony"
        });
        IMPLIED_FEATURES.put("android.permission.NFC", new String[] {
                "android.hardware.nfc"
        });
    }

    private boolean mIsTvApp;
    private XmlContext mXmlContext;
    private final java.util.List<Element> mPermissions = new java.util.ArrayList<>();
    private final java.util.Map<String, Boolean> mFeatures = new java.util.HashMap<>();

    @Override
    public Collection<String> getApplicableElements() {
        return java.util.Arrays.asList(
                "uses-permission",
                "uses-feature",
                "category"
        );
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIsTvApp = false;
        mXmlContext = null;
        mPermissions.clear();
        mFeatures.clear();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        mXmlContext = context;
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
            if (!name.isEmpty()) {
                mFeatures.put(name, required);
            }
        } else if ("category".equals(tagName)) {
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if ("android.intent.category.LEANBACK_LAUNCHER".equals(name)) {
                mIsTvApp = true;
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!mIsTvApp || mXmlContext == null) {
            return;
        }

        for (Element permissionElement : mPermissions) {
            String permissionName = permissionElement.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if (IMPLIED_FEATURES.containsKey(permissionName)) {
                String[] implied = IMPLIED_FEATURES.get(permissionName);
                for (String feature : implied) {
                    Boolean required = mFeatures.get(feature);
                    if (required == null || required) {
                        String message = String.format(
                                "Permission `%1$s` implies required hardware `%2$s` which is " +
                                "not supported on Android TV. Consider adding `<uses-feature " +
                                "android:name=\"%2$s\" android:required=\"false\" />` to the manifest.",
                                permissionName, feature);
                        mXmlContext.report(
                                ISSUE,
                                permissionElement,
                                mXmlContext.getNameLocation(permissionElement),
                                message
                        );
                    }
                }
            }
        }
    }
}
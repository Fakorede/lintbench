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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        IMPLIED_FEATURES.put("android.permission.CAMERA", 
                Arrays.asList("android.hardware.camera"));
        IMPLIED_FEATURES.put("android.permission.RECORD_AUDIO", 
                Arrays.asList("android.hardware.microphone"));
        IMPLIED_FEATURES.put("android.permission.ACCESS_FINE_LOCATION", 
                Arrays.asList("android.hardware.location.gps", "android.hardware.location"));
        IMPLIED_FEATURES.put("android.permission.ACCESS_COARSE_LOCATION", 
                Arrays.asList("android.hardware.location"));
        
        List<String> telephony = Arrays.asList("android.hardware.telephony");
        IMPLIED_FEATURES.put("android.permission.CALL_PHONE", telephony);
        IMPLIED_FEATURES.put("android.permission.SEND_SMS", telephony);
        IMPLIED_FEATURES.put("android.permission.RECEIVE_SMS", telephony);
        IMPLIED_FEATURES.put("android.permission.READ_SMS", telephony);
        IMPLIED_FEATURES.put("android.permission.RECEIVE_WAP_PUSH", telephony);
        IMPLIED_FEATURES.put("android.permission.RECEIVE_MMS", telephony);
        IMPLIED_FEATURES.put("android.permission.PROCESS_OUTGOING_CALLS", telephony);
        IMPLIED_FEATURES.put("android.permission.WRITE_SMS", telephony);
        
        IMPLIED_FEATURES.put("android.permission.NFC", 
                Arrays.asList("android.hardware.nfc"));
    }

    private final Map<String, Element> mDeclaredPermissions = new HashMap<>();
    private final Set<String> mFeaturesNotRequired = new HashSet<>();
    private boolean mIsTvApp = false;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("uses-permission", "uses-feature", "category");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mDeclaredPermissions.clear();
        mFeaturesNotRequired.clear();
        mIsTvApp = false;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if ("uses-permission".equals(tagName)) {
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if (name != null && !name.isEmpty()) {
                mDeclaredPermissions.put(name, element);
            }
        } else if ("uses-feature".equals(tagName)) {
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if (name != null && !name.isEmpty()) {
                if ("android.software.leanback".equals(name)) {
                    mIsTvApp = true;
                }
                String requiredStr = element.getAttributeNS("http://schemas.android.com/apk/res/android", "required");
                boolean required = requiredStr.isEmpty() || Boolean.parseBoolean(requiredStr);
                if (!required) {
                    mFeaturesNotRequired.add(name);
                }
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
        if (!mIsTvApp || !(context instanceof XmlContext)) {
            return;
        }
        XmlContext xmlContext = (XmlContext) context;

        for (Map.Entry<String, Element> entry : mDeclaredPermissions.entrySet()) {
            String permission = entry.getKey();
            Element element = entry.getValue();

            List<String> implied = IMPLIED_FEATURES.get(permission);
            if (implied != null) {
                for (String feature : implied) {
                    if (!mFeaturesNotRequired.contains(feature)) {
                        String message = String.format(
                                "Permission implies '%1$s' hardware but there is no corresponding " +
                                "<uses-feature android:name=\"%1$s\" android:required=\"false\" /> element",
                                feature);
                        xmlContext.report(ISSUE, element, xmlContext.getLocation(element), message);
                    }
                }
            }
        }
    }
}
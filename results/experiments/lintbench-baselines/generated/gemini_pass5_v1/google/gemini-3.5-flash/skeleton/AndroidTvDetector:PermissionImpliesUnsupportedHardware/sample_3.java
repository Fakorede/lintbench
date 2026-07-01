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
                    "The `<uses-permission>` element should not require a permission that "
                            + "implies an unsupported TV hardware feature. Google Play assumes "
                            + "that certain hardware related permissions indicate that the "
                            + "underlying hardware features are required by default. To fix "
                            + "the issue, consider declaring the corresponding `uses-feature` "
                            + "element with `required=\"false\"` attribute.",
                    Category.CORRECTNESS,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final Map<String, String[]> IMPLIED_FEATURES = new HashMap<>();

    static {
        IMPLIED_FEATURES.put("android.permission.CAMERA", new String[] {
            "android.hardware.camera", "android.hardware.camera.autofocus"
        });
        IMPLIED_FEATURES.put("android.permission.RECORD_AUDIO", new String[] {
            "android.hardware.microphone"
        });
        IMPLIED_FEATURES.put("android.permission.ACCESS_FINE_LOCATION", new String[] {
            "android.hardware.location.gps", "android.hardware.location"
        });
        IMPLIED_FEATURES.put("android.permission.ACCESS_COARSE_LOCATION", new String[] {
            "android.hardware.location.network", "android.hardware.location"
        });
        
        String[] telephony = new String[] { "android.hardware.telephony" };
        IMPLIED_FEATURES.put("android.permission.CALL_PHONE", telephony);
        IMPLIED_FEATURES.put("android.permission.CALL_PRIVILEGED", telephony);
        IMPLIED_FEATURES.put("android.permission.MODIFY_PHONE_STATE", telephony);
        IMPLIED_FEATURES.put("android.permission.PROCESS_OUTGOING_CALLS", telephony);
        IMPLIED_FEATURES.put("android.permission.READ_PHONE_STATE", telephony);
        IMPLIED_FEATURES.put("android.permission.RECEIVE_MMS", telephony);
        IMPLIED_FEATURES.put("android.permission.RECEIVE_SMS", telephony);
        IMPLIED_FEATURES.put("android.permission.RECEIVE_WAP_PUSH", telephony);
        IMPLIED_FEATURES.put("android.permission.SEND_SMS", telephony);
        IMPLIED_FEATURES.put("android.permission.WRITE_APN_SETTINGS", telephony);
        IMPLIED_FEATURES.put("android.permission.WRITE_SMS", telephony);
        
        IMPLIED_FEATURES.put("android.permission.NFC", new String[] { "android.hardware.nfc" });
    }

    private List<Element> mPermissions;
    private Map<String, Boolean> mFeatures;
    private boolean mIsTvApp;

    @Override
    public Collection<String> getApplicableElements() {
        List<String> elements = new ArrayList<>();
        elements.add("uses-permission");
        elements.add("uses-feature");
        elements.add("category");
        return elements;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mPermissions = new ArrayList<>();
        mFeatures = new HashMap<>();
        mIsTvApp = false;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if ("uses-permission".equals(tagName)) {
            mPermissions.add(element);
        } else if ("uses-feature".equals(tagName)) {
            String name = element.getAttributeNS(ANDROID_URI, "name");
            if (name != null && !name.isEmpty()) {
                String requiredStr = element.getAttributeNS(ANDROID_URI, "required");
                boolean required = requiredStr == null || requiredStr.isEmpty() || !"false".equalsIgnoreCase(requiredStr);
                mFeatures.put(name, required);
                if ("android.software.leanback".equals(name)) {
                    mIsTvApp = true;
                }
            }
        } else if ("category".equals(tagName)) {
            String name = element.getAttributeNS(ANDROID_URI, "name");
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

        for (Element permissionElement : mPermissions) {
            String permissionName = permissionElement.getAttributeNS(ANDROID_URI, "name");
            if (permissionName == null || permissionName.isEmpty()) {
                continue;
            }

            String[] implied = IMPLIED_FEATURES.get(permissionName);
            if (implied != null) {
                List<String> missingFeatures = new ArrayList<>();
                for (String feature : implied) {
                    Boolean required = mFeatures.get(feature);
                    if (required == null || required) {
                        missingFeatures.add(feature);
                    }
                }

                if (!missingFeatures.isEmpty()) {
                    XmlContext xmlContext = (XmlContext) context;
                    String featureList = join(missingFeatures);
                    String msg = "Permission `" + permissionName + "` implies '" + featureList 
                        + "' hardware which is not supported on Android TV. "
                        + "Consider declaring the corresponding `<uses-feature>` element(s) with `android:required=\"false\"`.";
                    
                    xmlContext.report(ISSUE, permissionElement, xmlContext.getLocation(permissionElement), msg);
                }
            }
        }
    }

    private static String join(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(list.get(i));
        }
        return sb.toString();
    }
}
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "PermissionImpliesUnsupportedHardware",
                    "Permission Implies Unsupported Hardware",
                    "The `<uses-permission>` element should not require a permission that implies"
                            + " an unsupported TV hardware feature. Google Play assumes that"
                            + " certain hardware related permissions indicate that the underlying"
                            + " hardware features are required by default. To fix the issue,"
                            + " consider declaring the corresponding `<uses-feature>` element"
                            + " with `required=\"false\"` attribute.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String TAG_USES_PERMISSION = "uses-permission";
    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_REQUIRED = "required";
    private static final String VALUE_FALSE = "false";

    private static final Map<String, String> PERMISSION_TO_FEATURE;

    static {
        Map<String, String> map = new HashMap<>();
        map.put("android.permission.CAMERA", "android.hardware.camera");
        map.put("android.permission.CALL_PHONE", "android.hardware.telephony");
        map.put("android.permission.CALL_PRIVILEGED", "android.hardware.telephony");
        map.put("android.permission.MODIFY_PHONE_STATE", "android.hardware.telephony");
        map.put("android.permission.PROCESS_OUTGOING_CALLS", "android.hardware.telephony");
        map.put("android.permission.READ_PHONE_STATE", "android.hardware.telephony");
        map.put("android.permission.READ_SMS", "android.hardware.telephony");
        map.put("android.permission.RECEIVE_SMS", "android.hardware.telephony");
        map.put("android.permission.RECEIVE_MMS", "android.hardware.telephony");
        map.put("android.permission.RECEIVE_WAP_PUSH", "android.hardware.telephony");
        map.put("android.permission.SEND_SMS", "android.hardware.telephony");
        map.put("android.permission.WRITE_APN_SETTINGS", "android.hardware.telephony");
        map.put("android.permission.WRITE_SMS", "android.hardware.telephony");
        map.put("android.permission.ACCESS_COARSE_LOCATION", "android.hardware.location");
        map.put("android.permission.ACCESS_FINE_LOCATION", "android.hardware.location");
        map.put("android.permission.ACCESS_MOCK_LOCATION", "android.hardware.location");
        map.put("android.permission.ACCESS_LOCATION_EXTRA_COMMANDS", "android.hardware.location");
        map.put("android.permission.NFC", "android.hardware.nfc");
        map.put("android.permission.RECORD_AUDIO", "android.hardware.microphone");
        map.put("android.permission.USE_SIP", "android.hardware.sip");
        map.put("android.permission.UWB_RANGING", "android.hardware.uwb");
        PERMISSION_TO_FEATURE = Collections.unmodifiableMap(map);
    }

    private final Map<String, Boolean> mDeclaredFeatures = new HashMap<>();
    private final List<Element> mPermissionElements = new ArrayList<>();

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_USES_PERMISSION, TAG_USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(Context context) {
        mDeclaredFeatures.clear();
        mPermissionElements.clear();
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();
        if (TAG_USES_FEATURE.equals(tag)) {
            String name = getAttribute(element, ATTR_NAME);
            if (name != null && !name.isEmpty()) {
                String required = getAttribute(element, ATTR_REQUIRED);
                boolean requiredFalse =
                        required != null && VALUE_FALSE.equalsIgnoreCase(required);
                if (requiredFalse) {
                    mDeclaredFeatures.put(name, Boolean.FALSE);
                } else if (!mDeclaredFeatures.containsKey(name)) {
                    // Default for uses-feature is required="true".
                    mDeclaredFeatures.put(name, Boolean.TRUE);
                }
            }
        } else if (TAG_USES_PERMISSION.equals(tag)) {
            String name = getAttribute(element, ATTR_NAME);
            if (name != null && PERMISSION_TO_FEATURE.containsKey(name)) {
                mPermissionElements.add(element);
            }
        }
    }

    @Override
    public void afterCheckFile(Context context) {
        if (!(context instanceof XmlContext)) {
            return;
        }
        XmlContext xmlContext = (XmlContext) context;
        for (Element permissionElement : mPermissionElements) {
            String permissionName = getAttribute(permissionElement, ATTR_NAME);
            String feature = PERMISSION_TO_FEATURE.get(permissionName);
            if (feature == null) {
                continue;
            }

            Boolean required = mDeclaredFeatures.get(feature);
            if (required != null && !required) {
                continue;
            }

            String message =
                    String.format(
                            "Permission `%1$s` implies feature `%2$s`, which is not supported on"
                                    + " Android TV. Consider adding `<uses-feature"
                                    + " android:name=\"%2$s\" android:required=\"false\" />`.",
                            permissionName, feature);
            xmlContext.report(
                    ISSUE, permissionElement, xmlContext.getLocation(permissionElement), message);
        }
    }

    private static String getAttribute(Element element, String localName) {
        String value = element.getAttributeNS(ANDROID_URI, localName);
        if (value.isEmpty()) {
            value = element.getAttribute(localName);
        }
        if (value.isEmpty()) {
            value = element.getAttribute("android:" + localName);
        }
        return value.isEmpty() ? null : value;
    }
}
package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "PermissionImpliesUnsupportedHardware",
            "Permission implies unsupported TV hardware feature",
            "The `<uses-permission>` element should not require a permission that implies "
                    + "an unsupported TV hardware feature. Google Play assumes that certain "
                    + "hardware related permissions indicate that the underlying hardware "
                    + "features are required by default. To fix the issue, consider declaring "
                    + "the corresponding `<uses-feature>` element with "
                    + "`android:required=\"false\"`.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private static final String FEATURE_TELEVISION = "android.hardware.type.television";
    private static final String CATEGORY_LEANBACK_LAUNCHER =
            "android.intent.category.LEANBACK_LAUNCHER";

    private static final String FEATURE_CAMERA = "android.hardware.camera";
    private static final String FEATURE_MICROPHONE = "android.hardware.microphone";
    private static final String FEATURE_LOCATION_GPS = "android.hardware.location.gps";
    private static final String FEATURE_LOCATION_NETWORK = "android.hardware.location.network";
    private static final String FEATURE_TELEPHONY = "android.hardware.telephony";

    private static final String PERMISSION_CAMERA = "android.permission.CAMERA";
    private static final String PERMISSION_RECORD_AUDIO = "android.permission.RECORD_AUDIO";
    private static final String PERMISSION_ACCESS_FINE_LOCATION =
            "android.permission.ACCESS_FINE_LOCATION";
    private static final String PERMISSION_ACCESS_COARSE_LOCATION =
            "android.permission.ACCESS_COARSE_LOCATION";
    private static final String PERMISSION_CALL_PHONE = "android.permission.CALL_PHONE";
    private static final String PERMISSION_CALL_PRIVILEGED = "android.permission.CALL_PRIVILEGED";
    private static final String PERMISSION_MODIFY_PHONE_STATE =
            "android.permission.MODIFY_PHONE_STATE";
    private static final String PERMISSION_PROCESS_OUTGOING_CALLS =
            "android.permission.PROCESS_OUTGOING_CALLS";
    private static final String PERMISSION_READ_SMS = "android.permission.READ_SMS";
    private static final String PERMISSION_RECEIVE_SMS = "android.permission.RECEIVE_SMS";
    private static final String PERMISSION_SEND_SMS = "android.permission.SEND_SMS";
    private static final String PERMISSION_WRITE_APN_SETTINGS =
            "android.permission.WRITE_APN_SETTINGS";
    private static final String PERMISSION_READ_PHONE_STATE = "android.permission.READ_PHONE_STATE";
    private static final String PERMISSION_RECEIVE_MMS = "android.permission.RECEIVE_MMS";
    private static final String PERMISSION_RECEIVE_WAP_PUSH =
            "android.permission.RECEIVE_WAP_PUSH";
    private static final String PERMISSION_SEND_RESPOND_VIA_MESSAGE =
            "android.permission.SEND_RESPOND_VIA_MESSAGE";
    private static final String PERMISSION_READ_CALL_LOG = "android.permission.READ_CALL_LOG";
    private static final String PERMISSION_WRITE_CALL_LOG = "android.permission.WRITE_CALL_LOG";
    private static final String PERMISSION_ADD_VOICEMAIL = "android.permission.ADD_VOICEMAIL";
    private static final String PERMISSION_ANSWER_PHONE_CALLS =
            "android.permission.ANSWER_PHONE_CALLS";
    private static final String PERMISSION_READ_PHONE_NUMBERS =
            "android.permission.READ_PHONE_NUMBERS";

    private static final Map<String, String> PERMISSION_TO_FEATURE;

    static {
        Map<String, String> map = new HashMap<>();
        map.put(PERMISSION_CAMERA, FEATURE_CAMERA);
        map.put(PERMISSION_RECORD_AUDIO, FEATURE_MICROPHONE);
        map.put(PERMISSION_ACCESS_FINE_LOCATION, FEATURE_LOCATION_GPS);
        map.put(PERMISSION_ACCESS_COARSE_LOCATION, FEATURE_LOCATION_NETWORK);
        map.put(PERMISSION_CALL_PHONE, FEATURE_TELEPHONY);
        map.put(PERMISSION_CALL_PRIVILEGED, FEATURE_TELEPHONY);
        map.put(PERMISSION_MODIFY_PHONE_STATE, FEATURE_TELEPHONY);
        map.put(PERMISSION_PROCESS_OUTGOING_CALLS, FEATURE_TELEPHONY);
        map.put(PERMISSION_READ_SMS, FEATURE_TELEPHONY);
        map.put(PERMISSION_RECEIVE_SMS, FEATURE_TELEPHONY);
        map.put(PERMISSION_SEND_SMS, FEATURE_TELEPHONY);
        map.put(PERMISSION_WRITE_APN_SETTINGS, FEATURE_TELEPHONY);
        map.put(PERMISSION_READ_PHONE_STATE, FEATURE_TELEPHONY);
        map.put(PERMISSION_RECEIVE_MMS, FEATURE_TELEPHONY);
        map.put(PERMISSION_RECEIVE_WAP_PUSH, FEATURE_TELEPHONY);
        map.put(PERMISSION_SEND_RESPOND_VIA_MESSAGE, FEATURE_TELEPHONY);
        map.put(PERMISSION_READ_CALL_LOG, FEATURE_TELEPHONY);
        map.put(PERMISSION_WRITE_CALL_LOG, FEATURE_TELEPHONY);
        map.put(PERMISSION_ADD_VOICEMAIL, FEATURE_TELEPHONY);
        map.put(PERMISSION_ANSWER_PHONE_CALLS, FEATURE_TELEPHONY);
        map.put(PERMISSION_READ_PHONE_NUMBERS, FEATURE_TELEPHONY);
        PERMISSION_TO_FEATURE = Collections.unmodifiableMap(map);
    }

    private final Map<String, Boolean> mFeatures = new HashMap<>();
    private final List<Element> mPermissions = new ArrayList<>();
    private boolean mIsTv;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                SdkConstants.TAG_USES_PERMISSION,
                SdkConstants.TAG_USES_FEATURE,
                "category");
    }

    @Override
    public void beforeCheckFile(Context context) {
        mFeatures.clear();
        mPermissions.clear();
        mIsTv = false;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();
        if (SdkConstants.TAG_USES_FEATURE.equals(tag)) {
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI,
                    SdkConstants.ATTR_NAME);
            if (!name.isEmpty()) {
                String required = element.getAttributeNS(SdkConstants.ANDROID_URI,
                        SdkConstants.ATTR_REQUIRED);
                boolean isRequired = required.isEmpty()
                        || SdkConstants.VALUE_TRUE.equals(required);
                mFeatures.put(name, isRequired);
                if (FEATURE_TELEVISION.equals(name) && isRequired) {
                    mIsTv = true;
                }
            }
        } else if (SdkConstants.TAG_USES_PERMISSION.equals(tag)) {
            mPermissions.add(element);
        } else if ("category".equals(tag)) {
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI,
                    SdkConstants.ATTR_NAME);
            if (CATEGORY_LEANBACK_LAUNCHER.equals(name)) {
                mIsTv = true;
            }
        }
    }

    @Override
    public void afterCheckFile(Context context) {
        if (!mIsTv) {
            return;
        }
        XmlContext xmlContext = (XmlContext) context;
        for (Element permission : mPermissions) {
            String name = permission.getAttributeNS(SdkConstants.ANDROID_URI,
                    SdkConstants.ATTR_NAME);
            String feature = PERMISSION_TO_FEATURE.get(name);
            if (feature == null) {
                continue;
            }
            Boolean required = mFeatures.get(feature);
            if (required == null || required) {
                String message = String.format(
                        "Permission `%1$s` implies feature `%2$s` which is not supported on TV. "
                                + "Consider adding a `<uses-feature>` element with "
                                + "`android:required=\"false\"`.",
                        name, feature);
                xmlContext.report(ISSUE, xmlContext.getLocation(permission), message);
            }
        }
    }
}
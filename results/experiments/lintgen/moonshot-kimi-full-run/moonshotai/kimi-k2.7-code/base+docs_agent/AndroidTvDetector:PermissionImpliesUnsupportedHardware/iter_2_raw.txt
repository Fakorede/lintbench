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

import org.w3c.dom.Document;
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
                    + "the corresponding `<uses-feature>` element with `required=\"false\"`.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE),
            "https://developer.android.com/guide/topics/manifest/uses-feature-element.html#permissions");

    private static final String FEATURE_TELEVISION = "android.hardware.type.television";
    private static final String CATEGORY_LEANBACK_LAUNCHER =
            "android.intent.category.LEANBACK_LAUNCHER";

    private static final Map<String, String> PERMISSION_TO_FEATURE;

    static {
        Map<String, String> map = new HashMap<>();
        map.put(SdkConstants.ANDROID_PERMISSION_CAMERA, SdkConstants.FEATURE_CAMERA);
        map.put(SdkConstants.ANDROID_PERMISSION_RECORD_AUDIO, SdkConstants.FEATURE_MICROPHONE);
        map.put(SdkConstants.ANDROID_PERMISSION_ACCESS_FINE_LOCATION,
                SdkConstants.FEATURE_LOCATION_GPS);
        map.put(SdkConstants.ANDROID_PERMISSION_ACCESS_COARSE_LOCATION,
                SdkConstants.FEATURE_LOCATION_NETWORK);
        map.put(SdkConstants.ANDROID_PERMISSION_CALL_PHONE, SdkConstants.FEATURE_TELEPHONY);
        map.put(SdkConstants.ANDROID_PERMISSION_CALL_PRIVILEGED, SdkConstants.FEATURE_TELEPHONY);
        map.put(SdkConstants.ANDROID_PERMISSION_MODIFY_PHONE_STATE, SdkConstants.FEATURE_TELEPHONY);
        map.put(SdkConstants.ANDROID_PERMISSION_PROCESS_OUTGOING_CALLS,
                SdkConstants.FEATURE_TELEPHONY);
        map.put(SdkConstants.ANDROID_PERMISSION_READ_SMS, SdkConstants.FEATURE_TELEPHONY);
        map.put(SdkConstants.ANDROID_PERMISSION_RECEIVE_SMS, SdkConstants.FEATURE_TELEPHONY);
        map.put(SdkConstants.ANDROID_PERMISSION_SEND_SMS, SdkConstants.FEATURE_TELEPHONY);
        map.put(SdkConstants.ANDROID_PERMISSION_WRITE_APN_SETTINGS, SdkConstants.FEATURE_TELEPHONY);
        map.put(SdkConstants.ANDROID_PERMISSION_READ_PHONE_STATE, SdkConstants.FEATURE_TELEPHONY);
        map.put(SdkConstants.ANDROID_PERMISSION_RECEIVE_MMS, SdkConstants.FEATURE_TELEPHONY);
        map.put(SdkConstants.ANDROID_PERMISSION_RECEIVE_WAP_PUSH, SdkConstants.FEATURE_TELEPHONY);
        map.put(SdkConstants.ANDROID_PERMISSION_SEND_RESPOND_VIA_MESSAGE,
                SdkConstants.FEATURE_TELEPHONY);
        map.put(SdkConstants.ANDROID_PERMISSION_READ_CALL_LOG, SdkConstants.FEATURE_TELEPHONY);
        map.put(SdkConstants.ANDROID_PERMISSION_WRITE_CALL_LOG, SdkConstants.FEATURE_TELEPHONY);
        map.put(SdkConstants.ANDROID_PERMISSION_ADD_VOICEMAIL, SdkConstants.FEATURE_TELEPHONY);
        map.put(SdkConstants.ANDROID_PERMISSION_ANSWER_PHONE_CALLS, SdkConstants.FEATURE_TELEPHONY);
        map.put(SdkConstants.ANDROID_PERMISSION_READ_PHONE_NUMBERS, SdkConstants.FEATURE_TELEPHONY);
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
                SdkConstants.TAG_CATEGORY);
    }

    @Override
    public void beforeCheckFile(Context context) {
        mFeatures.clear();
        mPermissions.clear();
        mIsTv = false;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Document document = element.getOwnerDocument();
        if (document == null) {
            return;
        }
        Element root = document.getDocumentElement();
        if (root == null || !SdkConstants.TAG_MANIFEST.equals(root.getTagName())) {
            return;
        }

        String tag = element.getTagName();
        if (SdkConstants.TAG_USES_FEATURE.equals(tag)) {
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
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
        } else if (SdkConstants.TAG_CATEGORY.equals(tag)) {
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
            if (CATEGORY_LEANBACK_LAUNCHER.equals(name)) {
                mIsTv = true;
            }
        }
    }

    @Override
    public void afterCheckFile(Context context) {
        if (!mIsTv || !(context instanceof XmlContext)) {
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
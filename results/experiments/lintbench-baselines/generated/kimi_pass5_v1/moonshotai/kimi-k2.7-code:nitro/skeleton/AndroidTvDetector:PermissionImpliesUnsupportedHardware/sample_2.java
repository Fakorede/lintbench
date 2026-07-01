package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Detector.XmlScanner;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.ArrayList;
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
    private static final String VALUE_FALSE = "false";

    private static final String EXPLANATION =
            "The `<uses-permission>` element should not require a permission that implies "
            + "an unsupported TV hardware feature. Google Play assumes that certain "
            + "hardware-related permissions indicate that the underlying hardware features "
            + "are required by default. To fix the issue, declare the corresponding "
            + "`<uses-feature>` element with `android:required=\"false\"` so that the app "
            + "remains available on devices without that hardware.";

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "PermissionImpliesUnsupportedHardware",
                    "Permission Implies Unsupported Hardware",
                    EXPLANATION,
                    Category.CORRECTNESS,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final Map<String, String[]> PERMISSION_TO_FEATURES;
    private static final Set<String> TV_UNSUPPORTED_FEATURES;

    static {
        Map<String, String[]> map = new HashMap<>();
        map.put("android.permission.ACCESS_FINE_LOCATION",
                new String[]{"android.hardware.location", "android.hardware.location.gps"});
        map.put("android.permission.CAMERA",
                new String[]{"android.hardware.camera", "android.hardware.camera.autofocus"});
        map.put("android.permission.FLASHLIGHT",
                new String[]{"android.hardware.camera.flash"});
        map.put("android.permission.NFC",
                new String[]{"android.hardware.nfc"});
        map.put("android.permission.CALL_PHONE",
                new String[]{"android.hardware.telephony"});
        map.put("android.permission.CALL_PRIVILEGED",
                new String[]{"android.hardware.telephony"});
        map.put("android.permission.MODIFY_PHONE_STATE",
                new String[]{"android.hardware.telephony"});
        map.put("android.permission.PROCESS_OUTGOING_CALLS",
                new String[]{"android.hardware.telephony"});
        map.put("android.permission.READ_PHONE_STATE",
                new String[]{"android.hardware.telephony"});
        map.put("android.permission.SEND_SMS",
                new String[]{"android.hardware.telephony"});
        map.put("android.permission.RECEIVE_SMS",
                new String[]{"android.hardware.telephony"});
        map.put("android.permission.READ_SMS",
                new String[]{"android.hardware.telephony"});
        map.put("android.permission.WRITE_SMS",
                new String[]{"android.hardware.telephony"});
        map.put("android.permission.BROADCAST_SMS",
                new String[]{"android.hardware.telephony"});
        map.put("android.permission.WRITE_APN_SETTINGS",
                new String[]{"android.hardware.telephony"});
        PERMISSION_TO_FEATURES = Collections.unmodifiableMap(map);

        Set<String> features = new HashSet<>();
        features.add("android.hardware.camera");
        features.add("android.hardware.camera.autofocus");
        features.add("android.hardware.camera.flash");
        features.add("android.hardware.location.gps");
        features.add("android.hardware.nfc");
        features.add("android.hardware.screen.portrait");
        features.add("android.hardware.telephony");
        features.add("android.hardware.touchscreen");
        TV_UNSUPPORTED_FEATURES = Collections.unmodifiableSet(features);
    }

    private List<PermissionInfo> mPermissions;
    private Set<String> mOptionalFeatures;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_USES_PERMISSION, TAG_USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mPermissions = new ArrayList<>();
        mOptionalFeatures = new HashSet<>();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (TAG_USES_PERMISSION.equals(tag)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (name != null && !name.isEmpty()) {
                mPermissions.add(new PermissionInfo(element, name));
            }
        } else if (TAG_USES_FEATURE.equals(tag)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (name != null && !name.isEmpty()) {
                String required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
                if (VALUE_FALSE.equals(required)) {
                    mOptionalFeatures.add(name);
                }
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!(context instanceof XmlContext)) {
            return;
        }
        XmlContext xmlContext = (XmlContext) context;
        for (PermissionInfo permission : mPermissions) {
            String[] features = PERMISSION_TO_FEATURES.get(permission.name);
            if (features == null) {
                continue;
            }
            for (String feature : features) {
                if (TV_UNSUPPORTED_FEATURES.contains(feature)
                        && !mOptionalFeatures.contains(feature)) {
                    String message = String.format(
                            "Permission `%1$s` implies the unsupported TV hardware feature "
                            + "`%2$s`. Consider adding `<uses-feature android:name=\"%2$s\" "
                            + "android:required=\"false\" />`.",
                            permission.name, feature);
                    xmlContext.report(
                            ISSUE,
                            permission.element,
                            xmlContext.getLocation(permission.element),
                            message);
                }
            }
        }
    }

    private static class PermissionInfo {
        final Element element;
        final String name;

        PermissionInfo(Element element, String name) {
            this.element = element;
            this.name = name;
        }
    }
}
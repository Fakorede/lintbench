package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_REQUIRED;
import static com.android.SdkConstants.TAG_USES_FEATURE;
import static com.android.SdkConstants.TAG_USES_PERMISSION;

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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "PermissionImpliesUnsupportedHardware",
            "Permission implies unsupported TV hardware",
            "The `<uses-permission>` element should not require a permission that implies "
                    + "an unsupported TV hardware feature. Google Play assumes that certain "
                    + "hardware related permissions indicate that the underlying hardware "
                    + "features are required by default. To fix the issue, consider declaring "
                    + "the corresponding `uses-feature` element with `required=\"false\"`.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE),
            "https://developer.android.com/guide/topics/manifest/uses-feature-element.html#permissions");

    private static final Map<String, String[]> PERMISSION_TO_FEATURES;
    static {
        Map<String, String[]> map = new HashMap<>();
        map.put("android.permission.CAMERA", new String[]{
                "android.hardware.camera",
                "android.hardware.camera.autofocus"});
        map.put("android.permission.FLASHLIGHT", new String[]{
                "android.hardware.camera.flash"});
        map.put("android.permission.ACCESS_FINE_LOCATION", new String[]{
                "android.hardware.location",
                "android.hardware.location.gps"});
        map.put("android.permission.ACCESS_COARSE_LOCATION", new String[]{
                "android.hardware.location",
                "android.hardware.location.network"});
        map.put("android.permission.ACCESS_MOCK_LOCATION", new String[]{
                "android.hardware.location"});
        map.put("android.permission.ACCESS_LOCATION_EXTRA_COMMANDS", new String[]{
                "android.hardware.location"});
        map.put("android.permission.INSTALL_LOCATION_PROVIDER", new String[]{
                "android.hardware.location"});
        map.put("android.permission.RECORD_AUDIO", new String[]{
                "android.hardware.microphone"});
        map.put("android.permission.CALL_PHONE", new String[]{
                "android.hardware.telephony"});
        map.put("android.permission.CALL_PRIVILEGED", new String[]{
                "android.hardware.telephony"});
        map.put("android.permission.MODIFY_PHONE_STATE", new String[]{
                "android.hardware.telephony"});
        map.put("android.permission.PROCESS_OUTGOING_CALLS", new String[]{
                "android.hardware.telephony"});
        map.put("android.permission.READ_PHONE_STATE", new String[]{
                "android.hardware.telephony"});
        map.put("android.permission.READ_SMS", new String[]{
                "android.hardware.telephony"});
        map.put("android.permission.RECEIVE_SMS", new String[]{
                "android.hardware.telephony"});
        map.put("android.permission.SEND_SMS", new String[]{
                "android.hardware.telephony"});
        map.put("android.permission.WRITE_SMS", new String[]{
                "android.hardware.telephony"});
        map.put("android.permission.BLUETOOTH", new String[]{
                "android.hardware.bluetooth"});
        map.put("android.permission.BLUETOOTH_ADMIN", new String[]{
                "android.hardware.bluetooth"});
        map.put("android.permission.NFC", new String[]{
                "android.hardware.nfc"});
        PERMISSION_TO_FEATURES = Collections.unmodifiableMap(map);
    }

    private final Map<Element, String> mPermissionElements = new LinkedHashMap<>();
    private final Set<String> mNotRequiredFeatures = new HashSet<>();

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mPermissionElements.clear();
        mNotRequiredFeatures.clear();
    }

    @Override
    @NonNull
    public List<String> getApplicableElements() {
        return Arrays.asList(TAG_USES_PERMISSION, TAG_USES_FEATURE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (TAG_USES_FEATURE.equals(tag)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (name.isEmpty()) {
                return;
            }
            String required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
            if ("false".equalsIgnoreCase(required)) {
                mNotRequiredFeatures.add(name);
            }
        } else if (TAG_USES_PERMISSION.equals(tag)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (name.isEmpty()) {
                return;
            }
            if (PERMISSION_TO_FEATURES.containsKey(name)) {
                mPermissionElements.put(element, name);
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mPermissionElements.isEmpty()) {
            return;
        }
        XmlContext xmlContext = (XmlContext) context;
        for (Map.Entry<Element, String> entry : mPermissionElements.entrySet()) {
            Element element = entry.getKey();
            String permission = entry.getValue();
            String[] impliedFeatures = PERMISSION_TO_FEATURES.get(permission);
            if (impliedFeatures == null) {
                continue;
            }

            List<String> missing = new ArrayList<>();
            for (String feature : impliedFeatures) {
                if (!mNotRequiredFeatures.contains(feature)) {
                    missing.add(feature);
                }
            }

            if (!missing.isEmpty()) {
                String featureList = String.join(", ", missing);
                String message = String.format(
                        "Permission `%s` implies the hardware feature%s `%s` which may not be "
                                + "supported on TVs. Consider adding `<uses-feature "
                                + "android:name=\"...\" android:required=\"false\" />`.",
                        permission,
                        missing.size() > 1 ? "s" : "",
                        featureList);
                xmlContext.report(
                        ISSUE,
                        element,
                        xmlContext.getLocation(element),
                        message);
            }
        }
    }
}
package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.SdkConstants;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.ArrayList;
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
                    "The `<uses-permission>` element should not require a permission that implies an unsupported TV hardware feature. "
                            + "Google Play assumes that certain hardware related permissions indicate that the underlying hardware features are required by default. "
                            + "To fix the issue, consider declaring the corresponding `<uses-feature>` element with `required=\"false\"` attribute.",
                    Category.CORRECTNESS,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final Map<String, List<String>> PERMISSION_TO_FEATURES = new HashMap<>();

    static {
        PERMISSION_TO_FEATURES.put("android.permission.ACCESS_COARSE_LOCATION",
                Arrays.asList("android.hardware.location", "android.hardware.location.network"));
        PERMISSION_TO_FEATURES.put("android.permission.ACCESS_FINE_LOCATION",
                Arrays.asList("android.hardware.location", "android.hardware.location.gps"));
        PERMISSION_TO_FEATURES.put("android.permission.ACCESS_LOCATION_EXTRA_COMMANDS",
                Arrays.asList("android.hardware.location"));
        PERMISSION_TO_FEATURES.put("android.permission.ACCESS_MOCK_LOCATION",
                Arrays.asList("android.hardware.location"));
        PERMISSION_TO_FEATURES.put("android.permission.CALL_PHONE",
                Arrays.asList("android.hardware.telephony"));
        PERMISSION_TO_FEATURES.put("android.permission.CALL_PRIVILEGED",
                Arrays.asList("android.hardware.telephony"));
        PERMISSION_TO_FEATURES.put("android.permission.CAMERA",
                Arrays.asList("android.hardware.camera", "android.hardware.camera.autofocus"));
        PERMISSION_TO_FEATURES.put("android.permission.FLASHLIGHT",
                Arrays.asList("android.hardware.camera", "android.hardware.camera.flash"));
        PERMISSION_TO_FEATURES.put("android.permission.MODIFY_PHONE_STATE",
                Arrays.asList("android.hardware.telephony"));
        PERMISSION_TO_FEATURES.put("android.permission.PROCESS_OUTGOING_CALLS",
                Arrays.asList("android.hardware.telephony"));
        PERMISSION_TO_FEATURES.put("android.permission.READ_SMS",
                Arrays.asList("android.hardware.telephony"));
        PERMISSION_TO_FEATURES.put("android.permission.RECEIVE_SMS",
                Arrays.asList("android.hardware.telephony"));
        PERMISSION_TO_FEATURES.put("android.permission.RECORD_AUDIO",
                Arrays.asList("android.hardware.microphone"));
        PERMISSION_TO_FEATURES.put("android.permission.SEND_SMS",
                Arrays.asList("android.hardware.telephony"));
    }

    private final List<Element> mPermissions = new ArrayList<>();
    private final List<Element> mFeatures = new ArrayList<>();

    @Override
    public Collection<String> getApplicableElements() {
        List<String> elements = new ArrayList<>(2);
        elements.add("uses-permission");
        elements.add("uses-feature");
        return elements;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mPermissions.clear();
        mFeatures.clear();
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!(context instanceof XmlContext)) {
            return;
        }
        XmlContext xmlContext = (XmlContext) context;

        Set<String> optionalFeatures = new HashSet<>();
        for (Element feature : mFeatures) {
            String name = feature.getAttributeNS(SdkConstants.ANDROID_URI, "name");
            if (!name.isEmpty()
                    && "false".equals(feature.getAttributeNS(SdkConstants.ANDROID_URI, "required"))) {
                optionalFeatures.add(name);
            }
        }

        for (Element permission : mPermissions) {
            String name = permission.getAttributeNS(SdkConstants.ANDROID_URI, "name");
            List<String> features = PERMISSION_TO_FEATURES.get(name);
            if (features == null) {
                continue;
            }

            for (String feature : features) {
                if (optionalFeatures.contains(feature)) {
                    continue;
                }

                String message = String.format(
                        "Permission `%1$s` implies feature `%2$s`, which is not supported on Android TV. "
                                + "Consider declaring `<uses-feature android:name=\"%2$s\" android:required=\"false\" />`.",
                        name, feature);
                xmlContext.report(ISSUE, permission, xmlContext.getLocation(permission), message);
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if ("uses-permission".equals(tag)) {
            mPermissions.add(element);
        } else if ("uses-feature".equals(tag)) {
            mFeatures.add(element);
        }
    }
}
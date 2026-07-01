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

    private static final String ANDROID_MANIFEST_XML = "AndroidManifest.xml";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final String TAG_USES_PERMISSION = "uses-permission";
    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_REQUIRED = "required";

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "PermissionImpliesUnsupportedHardware",
                    "Permission Implies Unsupported Hardware",
                    "The `<uses-permission>` element should not require a permission that "
                            + "implies an unsupported TV hardware feature. Google Play assumes "
                            + "that certain hardware related permissions indicate that the "
                            + "underlying hardware features are required by default. To fix the "
                            + "issue, consider declaring the corresponding `uses-feature` element "
                            + "with `required=\"false\"` attribute.",
                    Category.CORRECTNESS,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final Map<String, List<String>> IMPLIED_FEATURES;

    static {
        Map<String, List<String>> map = new HashMap<>();

        map.put("android.permission.CAMERA",
                Arrays.asList("android.hardware.camera", "android.hardware.camera.autofocus"));
        map.put("android.permission.RECORD_AUDIO",
                Collections.singletonList("android.hardware.microphone"));

        map.put("android.permission.ACCESS_FINE_LOCATION",
                Arrays.asList("android.hardware.location", "android.hardware.location.gps"));
        map.put("android.permission.ACCESS_COARSE_LOCATION",
                Collections.singletonList("android.hardware.location"));
        map.put("android.permission.ACCESS_LOCATION_EXTRA_COMMANDS",
                Collections.singletonList("android.hardware.location"));
        map.put("android.permission.CONTROL_LOCATION_UPDATES",
                Collections.singletonList("android.hardware.location"));

        map.put("android.permission.CALL_PHONE",
                Collections.singletonList("android.hardware.telephony"));
        map.put("android.permission.CALL_PRIVILEGED",
                Collections.singletonList("android.hardware.telephony"));
        map.put("android.permission.MODIFY_PHONE_STATE",
                Collections.singletonList("android.hardware.telephony"));
        map.put("android.permission.PROCESS_OUTGOING_CALLS",
                Collections.singletonList("android.hardware.telephony"));
        map.put("android.permission.READ_SMS",
                Collections.singletonList("android.hardware.telephony"));
        map.put("android.permission.RECEIVE_SMS",
                Collections.singletonList("android.hardware.telephony"));
        map.put("android.permission.RECEIVE_MMS",
                Collections.singletonList("android.hardware.telephony"));
        map.put("android.permission.SEND_SMS",
                Collections.singletonList("android.hardware.telephony"));
        map.put("android.permission.WRITE_SMS",
                Collections.singletonList("android.hardware.telephony"));

        IMPLIED_FEATURES = Collections.unmodifiableMap(map);
    }

    private final Set<String> mOptionalFeatures = new HashSet<>();
    private final Map<Element, List<String>> mPermissionFeatures = new HashMap<>();
    private boolean mCheckFile;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_USES_PERMISSION, TAG_USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mCheckFile = ANDROID_MANIFEST_XML.equals(context.file.getName());
        mOptionalFeatures.clear();
        mPermissionFeatures.clear();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!mCheckFile) {
            return;
        }

        String tag = element.getTagName();
        if (TAG_USES_PERMISSION.equals(tag)) {
            String permission = getAttribute(element, ATTR_NAME);
            List<String> features = IMPLIED_FEATURES.get(permission);
            if (features != null) {
                mPermissionFeatures.put(element, features);
            }
        } else if (TAG_USES_FEATURE.equals(tag)) {
            String feature = getAttribute(element, ATTR_NAME);
            if (!feature.isEmpty()) {
                String required = getAttribute(element, ATTR_REQUIRED);
                if ("false".equalsIgnoreCase(required)) {
                    mOptionalFeatures.add(feature);
                }
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!mCheckFile) {
            return;
        }

        for (Map.Entry<Element, List<String>> entry : mPermissionFeatures.entrySet()) {
            Element permissionElement = entry.getKey();
            String permissionName = getAttribute(permissionElement, ATTR_NAME);

            List<String> missing = new ArrayList<>();
            for (String feature : entry.getValue()) {
                if (!mOptionalFeatures.contains(feature)) {
                    missing.add(feature);
                }
            }

            if (!missing.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < missing.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append('\'').append(missing.get(i)).append('\'');
                }

                String message = String.format(
                        "Permission '%s' implies the unsupported TV hardware feature(s): %s. "
                                + "Consider declaring the corresponding <uses-feature> "
                                + "element(s) with required=\"false\".",
                        permissionName, sb.toString());

                context.report(
                        ISSUE,
                        permissionElement,
                        context.getLocation(permissionElement),
                        message);
            }
        }
    }

    private static String getAttribute(@NonNull Element element, @NonNull String attribute) {
        String value = element.getAttributeNS(ANDROID_URI, attribute);
        if (value.isEmpty()) {
            value = element.getAttribute("android:" + attribute);
        }
        return value;
    }
}
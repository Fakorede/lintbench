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

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

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
                            + "issue, consider declaring the corresponding `<uses-feature>` element "
                            + "with `required=\"false\"` attribute.",
                    Category.CORRECTNESS,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String TAG_USES_PERMISSION = "uses-permission";
    private static final String TAG_USES_FEATURE = "uses-feature";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_REQUIRED = "required";
    private static final String VALUE_FALSE = "false";

    private static final Map<String, String> PERMISSION_TO_FEATURE;

    static {
        Map<String, String> map = new HashMap<>();
        map.put("android.permission.RECORD_AUDIO", "android.hardware.microphone");
        map.put("android.permission.CAMERA", "android.hardware.camera");
        map.put("android.permission.CALL_PHONE", "android.hardware.telephony");
        map.put("android.permission.ACCESS_FINE_LOCATION", "android.hardware.location");
        map.put("android.permission.ACCESS_COARSE_LOCATION", "android.hardware.location");
        map.put("android.permission.BLUETOOTH", "android.hardware.bluetooth");
        map.put("android.permission.BLUETOOTH_ADMIN", "android.hardware.bluetooth");
        map.put("android.permission.NFC", "android.hardware.nfc");
        PERMISSION_TO_FEATURE = Collections.unmodifiableMap(map);
    }

    private final List<Element> mPermissions = new ArrayList<>();
    private final Set<String> mNotRequiredFeatures = new HashSet<>();

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_USES_PERMISSION, TAG_USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mPermissions.clear();
        mNotRequiredFeatures.clear();
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!(context instanceof XmlContext)) {
            return;
        }
        XmlContext xmlContext = (XmlContext) context;
        if (!xmlContext.isManifestFile()) {
            return;
        }

        for (Element permission : mPermissions) {
            String name = permission.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (name.isEmpty()) {
                continue;
            }
            String feature = PERMISSION_TO_FEATURE.get(name);
            if (feature == null) {
                continue;
            }
            if (mNotRequiredFeatures.contains(feature)) {
                continue;
            }

            String message =
                    String.format(
                            "Permission `%1$s` implies feature `%2$s`.",
                            name.substring(name.lastIndexOf('.') + 1), feature);
            xmlContext.report(
                    ISSUE, permission, xmlContext.getLocation(permission), message);
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!context.isManifestFile()) {
            return;
        }

        String tag = element.getTagName();
        if (TAG_USES_PERMISSION.equals(tag)) {
            mPermissions.add(element);
        } else if (TAG_USES_FEATURE.equals(tag)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (!name.isEmpty()) {
                String required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED);
                if (VALUE_FALSE.equals(required)) {
                    mNotRequiredFeatures.add(name);
                }
            }
        }
    }
}
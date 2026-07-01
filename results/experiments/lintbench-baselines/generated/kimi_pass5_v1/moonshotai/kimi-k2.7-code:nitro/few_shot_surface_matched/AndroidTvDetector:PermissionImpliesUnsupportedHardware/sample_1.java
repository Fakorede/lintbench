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

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "PermissionImpliesUnsupportedHardware",
                    "Permission Implies Unsupported Hardware",
                    "The `<uses-permission>` element should not require a permission that implies"
                            + " an unsupported TV hardware feature. Google Play assumes that"
                            + " certain hardware related permissions indicate that the underlying"
                            + " hardware features are required by default. To fix the issue, consider"
                            + " declaring the corresponding `<uses-feature>` element with"
                            + " `required=\"false\"` attribute.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final java.util.Map<String, String> PERMISSION_TO_FEATURE;
    static {
        java.util.Map<String, String> map = new java.util.HashMap<>();
        map.put("android.permission.ACCESS_FINE_LOCATION", "android.hardware.location.gps");
        map.put("android.permission.CAMERA", "android.hardware.camera");
        map.put("android.permission.RECORD_AUDIO", "android.hardware.microphone");
        PERMISSION_TO_FEATURE = java.util.Collections.unmodifiableMap(map);
    }

    private java.util.List<org.w3c.dom.Element> mPermissions;

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Collections.singletonList("uses-permission");
    }

    @Override
    public void beforeCheckFile(Context context) {
        mPermissions = new java.util.ArrayList<>();
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        String name = element.getAttributeNS(ANDROID_URI, "name");
        if (name == null || name.isEmpty()) {
            return;
        }
        if (PERMISSION_TO_FEATURE.containsKey(name)) {
            mPermissions.add(element);
        }
    }

    @Override
    public void afterCheckFile(Context context) {
        XmlContext xmlContext = (XmlContext) context;
        org.w3c.dom.NodeList features =
                xmlContext.document.getElementsByTagName("uses-feature");

        for (org.w3c.dom.Element permission : mPermissions) {
            String name = permission.getAttributeNS(ANDROID_URI, "name");
            String feature = PERMISSION_TO_FEATURE.get(name);

            boolean hasRequiredFalseFeature = false;
            for (int i = 0, n = features.getLength(); i < n; i++) {
                org.w3c.dom.Element usesFeature = (org.w3c.dom.Element) features.item(i);
                String featureName = usesFeature.getAttributeNS(ANDROID_URI, "name");
                if (feature.equals(featureName)) {
                    String required = usesFeature.getAttributeNS(ANDROID_URI, "required");
                    if ("false".equals(required)) {
                        hasRequiredFalseFeature = true;
                    }
                    break;
                }
            }

            if (!hasRequiredFalseFeature) {
                xmlContext.report(
                        ISSUE,
                        permission,
                        xmlContext.getLocation(permission),
                        String.format(
                                "Permission `%1$s` implies that the `%2$s` hardware feature is"
                                        + " required, which is not supported on Android TV."
                                        + " Consider adding a `<uses-feature"
                                        + " android:name=\"%2$s\""
                                        + " android:required=\"false\" />` element to the"
                                        + " manifest.",
                                name,
                                feature));
            }
        }
    }
}
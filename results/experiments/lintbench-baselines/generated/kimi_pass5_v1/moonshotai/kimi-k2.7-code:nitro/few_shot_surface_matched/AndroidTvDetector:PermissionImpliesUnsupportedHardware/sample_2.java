package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.xml.AndroidManifest.NODE_USES_FEATURE;
import static com.android.xml.AndroidManifest.NODE_USES_PERMISSION;

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
import java.util.List;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "PermissionImpliesUnsupportedHardware",
                    "Permission Implies Unsupported TV Hardware",
                    "The `<uses-permission>` element should not require a permission that implies"
                            + " an unsupported TV hardware feature. Google Play assumes that"
                            + " certain hardware-related permissions indicate that the underlying"
                            + " hardware features are required by default. To fix the issue,"
                            + " consider declaring the corresponding `<uses-feature>` element"
                            + " with `required=\"false\"`.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private List<Element> mPermissionElements;
    private List<String> mRequiredFalseFeatures;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(NODE_USES_PERMISSION, NODE_USES_FEATURE);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mPermissionElements = new ArrayList<>();
        mRequiredFalseFeatures = new ArrayList<>();
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        XmlContext xmlContext = (XmlContext) context;
        for (Element permission : mPermissionElements) {
            String permissionName = permission.getAttributeNS(ANDROID_URI, ATTR_NAME);
            String[] impliedFeatures = getImpliedFeatures(permissionName);
            if (impliedFeatures == null) {
                continue;
            }
            for (String feature : impliedFeatures) {
                if (!mRequiredFalseFeatures.contains(feature)) {
                    String message =
                            "Permission `"
                                    + permissionName
                                    + "` implies feature `"
                                    + feature
                                    + "`, which is not supported on Android TV. Consider adding"
                                    + " `<uses-feature android:name=\""
                                    + feature
                                    + "\" android:required=\"false\" />`.";
                    xmlContext.report(
                            ISSUE,
                            permission,
                            xmlContext.getLocation(permission),
                            message);
                }
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (NODE_USES_PERMISSION.equals(tag)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (!name.isEmpty() && getImpliedFeatures(name) != null) {
                mPermissionElements.add(element);
            }
        } else if (NODE_USES_FEATURE.equals(tag)) {
            String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            String required = element.getAttributeNS(ANDROID_URI, "required");
            if (!name.isEmpty() && "false".equals(required)) {
                mRequiredFalseFeatures.add(name);
            }
        }
    }

    private static String[] getImpliedFeatures(String permissionName) {
        switch (permissionName) {
            case "android.permission.CAMERA":
                return new String[] {
                    "android.hardware.camera", "android.hardware.camera.autofocus"
                };
            case "android.permission.RECORD_AUDIO":
                return new String[] {"android.hardware.microphone"};
            case "android.permission.ACCESS_FINE_LOCATION":
                return new String[] {"android.hardware.location.gps"};
            case "android.permission.CALL_PHONE":
            case "android.permission.CALL_PRIVILEGED":
            case "android.permission.MODIFY_PHONE_STATE":
            case "android.permission.PROCESS_OUTGOING_CALLS":
            case "android.permission.READ_PHONE_STATE":
            case "android.permission.READ_SMS":
            case "android.permission.RECEIVE_SMS":
            case "android.permission.RECEIVE_MMS":
            case "android.permission.RECEIVE_WAP_PUSH":
            case "android.permission.SEND_SMS":
            case "android.permission.WRITE_SMS":
                return new String[] {"android.hardware.telephony"};
            default:
                return null;
        }
    }
}
package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;

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
import org.w3c.dom.Element;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "UnsupportedTvHardware",
            "Unsupported TV Hardware Feature",
            "The `<uses-feature>` element should not require this unsupported TV hardware feature. "
                    + "Any uses-feature not explicitly marked with `required=\"false\"` is necessary on the device to be installed on. "
                    + "Ensure that any features that might prevent it from being installed on a TV device are reviewed and marked as not required in the manifest.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE));

    private static final Set<String> UNSUPPORTED_TV_FEATURES = new HashSet<>();
    static {
        UNSUPPORTED_TV_FEATURES.add("android.hardware.touchscreen");
        UNSUPPORTED_TV_FEATURES.add("android.hardware.camera");
        UNSUPPORTED_TV_FEATURES.add("android.hardware.camera.autofocus");
        UNSUPPORTED_TV_FEATURES.add("android.hardware.location.gps");
        UNSUPPORTED_TV_FEATURES.add("android.hardware.microphone");
        UNSUPPORTED_TV_FEATURES.add("android.hardware.nfc");
        UNSUPPORTED_TV_FEATURES.add("android.hardware.telephony");
    }

    private boolean isManifestFile;

    @NonNull
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("uses-feature");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        isManifestFile = context.file.getName().equals("AndroidManifest.xml");
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        isManifestFile = false;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!isManifestFile) {
            return;
        }

        String featureName = element.getAttributeNS(ANDROID_URI, "name");
        if (featureName.isEmpty() || !UNSUPPORTED_TV_FEATURES.contains(featureName)) {
            return;
        }

        String required = element.getAttributeNS(ANDROID_URI, "required");
        if ("false".equals(required)) {
            return;
        }

        context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Unsupported TV hardware feature: " + featureName + ". "
                        + "Mark as required=\"false\" to support TV devices.");
    }
}
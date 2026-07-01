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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final Set<String> UNSUPPORTED_FEATURES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "android.hardware.telephony",
            "android.hardware.camera",
            "android.hardware.camera.autofocus",
            "android.hardware.camera.flash",
            "android.hardware.camera.front",
            "android.hardware.nfc",
            "android.hardware.microphone",
            "android.hardware.touchscreen",
            "android.hardware.faketouch",
            "android.hardware.screen.portrait",
            "android.hardware.screen.landscape"
    )));

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "UnsupportedTvHardware",
                    "Unsupported TV Hardware Feature",
                    "The `<uses-feature>` element should not require this unsupported TV hardware feature. " +
                    "Any uses-feature not explicitly marked with `required=\"false\"` is necessary on the device to be installed on. " +
                    "Ensure that any features that might prevent it from being installed on a TV device are reviewed and marked as not required in the manifest.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("uses-feature");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        // No per-file setup required
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        // No per-file cleanup required
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(ANDROID_URI, "name");
        if (UNSUPPORTED_FEATURES.contains(name)) {
            String required = element.getAttributeNS(ANDROID_URI, "required");
            // If required attribute is missing or not explicitly "false", it defaults to true
            if (!"false".equals(required)) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "This feature is not supported on Android TV. " +
                        "Mark it as not required by adding android:required=\"false\".");
            }
        }
    }
}
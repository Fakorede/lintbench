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
import java.util.Collection;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "UnsupportedTvHardware",
                    "Unsupported TV Hardware Feature",
                    "The `<uses-feature>` element should not require this unsupported TV "
                            + "hardware feature. Any uses-feature not explicitly marked with "
                            + "`required=\"false\"` is necessary on the device to be installed "
                            + "on. Ensure that any features that might prevent it from being "
                            + "installed on a TV device are reviewed and marked as not "
                            + "required in the manifest.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private static final java.util.Set<String> UNSUPPORTED_FEATURES = new java.util.HashSet<>(
            java.util.Arrays.asList(
                    "android.hardware.touchscreen",
                    "android.hardware.telephony",
                    "android.hardware.camera",
                    "android.hardware.camera.autofocus"
            )
    );

    @Override
    public Collection<String> getApplicableElements() {
        return java.util.Collections.singletonList("uses-feature");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
        if (UNSUPPORTED_FEATURES.contains(name)) {
            String required = element.getAttributeNS("http://schemas.android.com/apk/res/android", "required");
            if (!"false".equals(required)) {
                context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "The " + name + " feature is not supported on TV and should be marked as required=\"false\"");
            }
        }
    }
}
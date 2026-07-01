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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final Set<String> UNSUPPORTED_TV_FEATURES =
            Collections.unmodifiableSet(
                    new HashSet<>(
                            Arrays.asList(
                                    "android.hardware.telephony",
                                    "android.hardware.camera",
                                    "android.hardware.nfc",
                                    "android.hardware.touchscreen",
                                    "android.hardware.faketouch",
                                    "android.hardware.location.gps")));

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "UnsupportedTvHardware",
                    "Unsupported TV Hardware Feature",
                    "The `<uses-feature>` element should not require this unsupported TV hardware "
                            + "feature. Any `<uses-feature>` not explicitly marked with "
                            + "`required=\"false\"` is necessary on the device to install the app. "
                            + "Ensure that any features that might prevent it from being installed "
                            + "on a TV device are reviewed and marked as not required in the "
                            + "manifest.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("uses-feature");
    }

    @Override
    public void beforeCheckFile(Context context) {
        // No per-file state is required for this check.
    }

    @Override
    public void afterCheckFile(Context context) {
        // No per-file state is required for this check.
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttributeNS(ANDROID_URI, "name");
        if (name == null || name.isEmpty()) {
            return;
        }

        if (UNSUPPORTED_TV_FEATURES.contains(name) && isRequired(element)) {
            String message =
                    "The uses-feature element should not require the unsupported TV hardware feature '"
                            + name
                            + "'";
            context.report(ISSUE, element, context.getLocation(element), message);
        }
    }

    private static boolean isRequired(Element element) {
        String required = element.getAttributeNS(ANDROID_URI, "required");
        if (required == null || required.isEmpty()) {
            return true;
        }
        return !required.equalsIgnoreCase("false") && !required.equals("0");
    }
}
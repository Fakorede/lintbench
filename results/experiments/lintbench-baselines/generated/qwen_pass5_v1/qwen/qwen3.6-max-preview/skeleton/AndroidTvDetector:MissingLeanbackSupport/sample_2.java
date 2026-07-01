package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements Detector.XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "MissingLeanbackSupport",
                    "Missing Leanback Support",
                    "The manifest should declare the use of the Leanback user interface "
                    + "required by Android TV.\n\n"
                    + "To fix this, add\n"
                    + "`<uses-feature android:name=\"android.software.leanback\"\n"
                    + "                android:required=\"false\" />`\n"
                    + "to your manifest.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private boolean mFoundLeanback;

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("uses-feature");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mFoundLeanback = false;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (!mFoundLeanback) {
            context.report(ISSUE, Location.create(context.file),
                    "The manifest should declare the use of the Leanback user interface "
                    + "required by Android TV.\n\n"
                    + "To fix this, add\n"
                    + "`<uses-feature android:name=\"android.software.leanback\"\n"
                    + "                android:required=\"false\" />`\n"
                    + "to your manifest.");
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(ANDROID_URI, "name");
        if ("android.software.leanback".equals(name)) {
            mFoundLeanback = true;
        }
    }
}
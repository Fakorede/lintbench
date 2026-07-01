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
            new Implementation(AndroidTvDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "MissingLeanbackSupport",
                    "Missing Leanback Support",
                    "The manifest should declare the use of the Leanback user interface " +
                    "required by Android TV.\n\n" +
                    "To fix this, add\n" +
                    "`<uses-feature android:name=\"android.software.leanback\" " +
                    "android:required=\"false\" />`\n" +
                    "to your manifest.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String LEANBACK_FEATURE = "android.software.leanback";

    private boolean mIsManifest;
    private boolean mFoundLeanback;

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("uses-feature");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIsManifest = "AndroidManifest.xml".equals(context.file.getName());
        mFoundLeanback = false;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mIsManifest && !mFoundLeanback) {
            context.report(ISSUE, Location.create(context.file),
                    "The manifest should declare the use of the Leanback user interface " +
                    "required by Android TV.");
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!mIsManifest) {
            return;
        }
        String name = element.getAttributeNS(ANDROID_URI, "name");
        if (LEANBACK_FEATURE.equals(name)) {
            mFoundLeanback = true;
        }
    }
}
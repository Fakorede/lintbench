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
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;

public class AndroidTvDetector extends Detector implements XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String LEANBACK_LAUNCHER = "android.intent.category.LEANBACK_LAUNCHER";

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "MissingLeanbackLauncher",
                    "Missing Leanback Launcher Intent Filter",
                    "An application intended to run on TV devices must declare a launcher activity for TV in its manifest using a `android.intent.category.LEANBACK_LAUNCHER` intent filter.",
                    Category.CORRECTNESS,
                    8,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean mIsManifest;
    private boolean mHasLeanbackLauncher;

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("category");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mIsManifest = context.file.getName().equals("AndroidManifest.xml");
        if (mIsManifest) {
            mHasLeanbackLauncher = false;
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mIsManifest && !mHasLeanbackLauncher) {
            context.report(ISSUE, Location.create(context.file),
                    "Missing Leanback Launcher Intent Filter: An application intended to run on TV devices must declare a launcher activity for TV in its manifest using a `android.intent.category.LEANBACK_LAUNCHER` intent filter.");
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!mIsManifest) {
            return;
        }
        String name = element.getAttributeNS(ANDROID_URI, "name");
        if (LEANBACK_LAUNCHER.equals(name)) {
            mHasLeanbackLauncher = true;
        }
    }
}
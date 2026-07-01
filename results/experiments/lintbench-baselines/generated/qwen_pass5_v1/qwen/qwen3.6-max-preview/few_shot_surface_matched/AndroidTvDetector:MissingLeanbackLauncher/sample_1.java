package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
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

public class AndroidTvDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "MissingLeanbackLauncher",
                    "Missing Leanback Launcher Intent Filter",
                    "An application intended to run on TV devices must declare a launcher "
                            + "activity for TV in its manifest using a "
                            + "`android.intent.category.LEANBACK_LAUNCHER` intent filter.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            AndroidTvDetector.class,
                            Scope.MANIFEST_SCOPE));

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_NAME = "name";
    private static final String TAG_CATEGORY = "category";
    private static final String LEANBACK_LAUNCHER_CATEGORY = "android.intent.category.LEANBACK_LAUNCHER";

    private boolean hasLeanbackLauncher;

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_CATEGORY);
    }

    @Override
    public void beforeCheckFile(@NonNull XmlContext context) {
        hasLeanbackLauncher = false;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (LEANBACK_LAUNCHER_CATEGORY.equals(name)) {
            hasLeanbackLauncher = true;
        }
    }

    @Override
    public void afterCheckFile(@NonNull XmlContext context) {
        if (!hasLeanbackLauncher) {
            Element root = context.document.getDocumentElement();
            context.report(
                    ISSUE,
                    context.getLocation(root),
                    "Missing Leanback launcher intent filter. TV apps must declare an activity "
                            + "with the `android.intent.category.LEANBACK_LAUNCHER` category.");
        }
    }
}
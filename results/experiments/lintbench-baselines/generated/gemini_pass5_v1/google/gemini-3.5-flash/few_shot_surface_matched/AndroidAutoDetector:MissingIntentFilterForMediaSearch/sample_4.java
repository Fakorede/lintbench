package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private boolean mHasMediaSearchIntent = false;
    private final java.util.List<com.android.tools.lint.detector.api.Location> mMediaServices =
            new java.util.ArrayList<com.android.tools.lint.detector.api.Location>();

    public static final Issue ISSUE =
            Issue.create(
                    "MissingIntentFilterForMediaSearch",
                    "Missing MEDIA_PLAY_FROM_SEARCH intent-filter",
                    "To support voice searches on Android Auto, you should also register an "
                            + "`intent-filter` for the action `android.media.action.MEDIA_PLAY_FROM_SEARCH` "
                            + "to your `<activity>` or `<service>`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            AndroidAutoDetector.class,
                            java.util.EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST)));

    @Override
    public boolean appliesTo(
            @com.android.annotations.NonNull com.android.tools.lint.detector.api.Context context,
            @com.android.annotations.NonNull java.io.File file) {
        return true;
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Arrays.asList("action", "service", "activity");
    }

    @Override
    public void beforeCheckRootProject(
            @com.android.annotations.NonNull com.android.tools.lint.detector.api.Context context) {
        mHasMediaSearchIntent = false;
        mMediaServices.clear();
    }

    @Override
    public void visitElement(
            @com.android.annotations.NonNull XmlContext context,
            @com.android.annotations.NonNull org.w3c.dom.Element element) {
        String tagName = element.getTagName();
        if ("action".equals(tagName)) {
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if ("android.media.action.MEDIA_PLAY_FROM_SEARCH".equals(name)) {
                mHasMediaSearchIntent = true;
            }
        }
    }

    @Override
    public java.util.List<String> applicableSuperClasses() {
        return java.util.Arrays.asList(
                "android.media.browse.MediaBrowserService",
                "androidx.media.MediaBrowserServiceCompat",
                "android.support.v4.media.MediaBrowserServiceCompat");
    }

    @Override
    public void visitClass(
            @com.android.annotations.NonNull JavaContext context,
            @com.android.annotations.NonNull UClass declaration) {
        mMediaServices.add(context.getNameLocation(declaration));
    }

    @Override
    public void visitMethod(
            @com.android.annotations.NonNull JavaContext context,
            @com.android.annotations.NonNull UCallExpression node,
            @com.android.annotations.NonNull PsiMethod method) {
        // No-op
    }

    @Override
    public void afterCheckRootProject(
            @com.android.annotations.NonNull com.android.tools.lint.detector.api.Context context) {
        if (!mHasMediaSearchIntent && !mMediaServices.isEmpty()) {
            for (com.android.tools.lint.detector.api.Location location : mMediaServices) {
                context.report(
                        ISSUE,
                        location,
                        "Missing intent-filter for Media Search. To support voice searches, "
                                + "register an intent-filter for action `android.media.action.MEDIA_PLAY_FROM_SEARCH` "
                                + "in your activity or service.");
            }
        }
    }
}
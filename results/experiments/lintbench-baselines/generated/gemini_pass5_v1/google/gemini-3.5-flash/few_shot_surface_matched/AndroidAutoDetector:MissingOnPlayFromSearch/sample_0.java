package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    AndroidAutoDetector.class,
                    java.util.EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)
            );

    public static final Issue ISSUE =
            Issue.create(
                    "MissingOnPlayFromSearch",
                    "Missing onPlayFromSearch",
                    "To support voice searches on Android Auto, in addition to adding an "
                            + "`intent-filter` for the action `onPlayFromSearch`, you also need to "
                            + "override and implement `onPlayFromSearch(String query, Bundle bundle)`",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION)
                    .setAndroidSpecific(true);

    private boolean mHasSearchIntent = false;
    private boolean mHasOnPlayFromSearch = false;
    private Location mSearchIntentLocation = null;

    public AndroidAutoDetector() {}

    @Override
    public boolean appliesTo(com.android.tools.lint.detector.api.Context context, java.io.File file) {
        return true;
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Collections.singletonList("action");
    }

    @Override
    public void beforeCheckRootProject(com.android.tools.lint.detector.api.Context context) {
        mHasSearchIntent = false;
        mHasOnPlayFromSearch = false;
        mSearchIntentLocation = null;
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        if ("action".equals(element.getTagName())) {
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if ("android.media.action.MEDIA_PLAY_FROM_SEARCH".equals(name)) {
                mHasSearchIntent = true;
                mSearchIntentLocation = context.getLocation(element);
            }
        }
    }

    @Override
    public java.util.List<String> applicableSuperClasses() {
        return java.util.Arrays.asList(
                "android.support.v4.media.session.MediaSessionCompat.Callback",
                "androidx.media.MediaSessionCompat.Callback",
                "android.media.session.MediaSession.Callback"
        );
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        for (PsiMethod method : declaration.getMethods()) {
            if ("onPlayFromSearch".equals(method.getName())) {
                mHasOnPlayFromSearch = true;
            }
        }
    }

    @Override
    public void visitMethod(JavaContext context, UCallExpression node, PsiMethod method) {
        // Required override for SourceCodeScanner
    }

    @Override
    public void afterCheckRootProject(com.android.tools.lint.detector.api.Context context) {
        if (mHasSearchIntent && !mHasOnPlayFromSearch && mSearchIntentLocation != null) {
            context.report(
                    ISSUE,
                    mSearchIntentLocation,
                    "To support voice searches on Android Auto, you must override and implement "
                            + "`onPlayFromSearch(String query, Bundle bundle)` in your MediaSession callback class."
            );
        }
    }
}
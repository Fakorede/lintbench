package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
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
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    AndroidAutoDetector.class,
                    java.util.EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE));

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
                    IMPLEMENTATION);

    private boolean mHasPlayFromSearchIntent = false;

    @Override
    public boolean appliesTo(Context context, java.io.File file) {
        return true;
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Collections.singletonList("action");
    }

    @Override
    public void beforeCheckRootProject(Context context) {
        mHasPlayFromSearchIntent = false;
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        if ("action".equals(element.getTagName())) {
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if (name == null || name.isEmpty()) {
                name = element.getAttribute("android:name");
            }
            if ("android.media.action.MEDIA_PLAY_FROM_SEARCH".equals(name)) {
                mHasPlayFromSearchIntent = true;
            }
        }
    }

    @Override
    public java.util.List<String> applicableSuperClasses() {
        return java.util.Arrays.asList(
                "android.media.session.MediaSession.Callback",
                "android.support.v4.media.session.MediaSessionCompat.Callback",
                "androidx.media.MediaSessionCompat.Callback"
        );
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        if (!mHasPlayFromSearchIntent) {
            return;
        }

        if (declaration.isInterface()) {
            return;
        }

        boolean overridesPlayFromSearch = false;
        for (PsiMethod method : declaration.getMethods()) {
            if ("onPlayFromSearch".equals(method.getName())) {
                overridesPlayFromSearch = true;
                break;
            }
        }

        if (!overridesPlayFromSearch) {
            context.report(
                    ISSUE,
                    declaration,
                    context.getNameLocation(declaration),
                    "This class extends a `MediaSession.Callback` but does not implement "
                            + "`onPlayFromSearch` to support voice search. In the manifest, "
                            + "an intent-filter with action `android.media.action.MEDIA_PLAY_FROM_SEARCH` "
                            + "was found."
            );
        }
    }

    @Override
    public void visitMethod(JavaContext context, UMethod method) {
        // Required override by specification
    }
}
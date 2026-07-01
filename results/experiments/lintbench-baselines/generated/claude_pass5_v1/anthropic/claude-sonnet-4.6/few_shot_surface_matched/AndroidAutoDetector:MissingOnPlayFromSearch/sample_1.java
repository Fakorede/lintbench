package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.w3c.dom.Element;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String ACTION_PLAY_FROM_SEARCH =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";

    private static final String MEDIA_SESSION_CALLBACK_CLASS =
            "android.support.v4.media.session.MediaSessionCompat.Callback";

    private static final String MEDIA_SESSION_CALLBACK_CLASS2 =
            "android.media.session.MediaSession.Callback";

    private static final String ON_PLAY_FROM_SEARCH = "onPlayFromSearch";

    private static final String ATTR_NAME = "android:name";

    private static final String TAG_ACTION = "action";

    /** Whether the manifest contains the play-from-search intent filter action */
    private boolean mHasPlayFromSearchAction = false;

    public static final Issue ISSUE =
            Issue.create(
                            "MissingOnPlayFromSearch",
                            "Missing `onPlayFromSearch`",
                            "To support voice searches on Android Auto, in addition to adding an "
                                    + "`intent-filter` for the action `onPlayFromSearch`, "
                                    + "you also need to override and implement "
                                    + "`onPlayFromSearch(String query, Bundle bundle)`",
                            Category.CORRECTNESS,
                            6,
                            Severity.ERROR,
                            new Implementation(
                                    AndroidAutoDetector.class,
                                    EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)))
                    .setAndroidSpecific(true)
                    .addMoreInfo(
                            "https://developer.android.com/training/auto/audio/index.html#support_voice");

    public AndroidAutoDetector() {}

    // ---- Implements Detector ----

    @Override
    public boolean appliesTo(@NonNull com.android.tools.lint.detector.api.ResourceFolderType folderType) {
        return false;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mHasPlayFromSearchAction = false;
    }

    // ---- Implements XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_ACTION);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute(ATTR_NAME);
        if (ACTION_PLAY_FROM_SEARCH.equals(name)) {
            mHasPlayFromSearchAction = true;
        }
    }

    // ---- Implements SourceCodeScanner ----

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(MEDIA_SESSION_CALLBACK_CLASS, MEDIA_SESSION_CALLBACK_CLASS2);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (!mHasPlayFromSearchAction) {
            return;
        }

        // Check if the class is an abstract class - if so, skip it
        if (declaration.hasModifierProperty("abstract")) {
            return;
        }

        // Check if onPlayFromSearch is implemented
        for (PsiMethod method : declaration.getMethods()) {
            if (isOnPlayFromSearchMethod(method)) {
                return;
            }
        }

        context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "To support voice searches on Android Auto, the `"
                        + declaration.getName()
                        + "` class should override `onPlayFromSearch(String query, Bundle bundle)`");
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UMethod method,
            @NonNull PsiMethod resolvedMethod) {
        // Not used in this detector
    }

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return null;
    }

    private static boolean isOnPlayFromSearchMethod(@NonNull PsiMethod method) {
        if (!ON_PLAY_FROM_SEARCH.equals(method.getName())) {
            return false;
        }
        PsiParameter[] parameters = method.getParameterList().getParameters();
        if (parameters.length != 2) {
            return false;
        }
        String firstType = parameters[0].getType().getCanonicalText();
        String secondType = parameters[1].getType().getCanonicalText();
        return "java.lang.String".equals(firstType) && "android.os.Bundle".equals(secondType);
    }
}
package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;

public class AndroidAutoDetector extends Detector implements Detector.UastScanner, Detector.XmlScanner {

    private static final String ACTION_PLAY_FROM_SEARCH =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";

    private static final String MEDIA_SESSION_CALLBACK_CLASS =
            "android.media.session.MediaSession.Callback";

    private static final String MEDIA_COMPAT_SESSION_CALLBACK_CLASS =
            "android.support.v4.media.session.MediaSessionCompat.Callback";

    private static final String ON_PLAY_FROM_SEARCH = "onPlayFromSearch";

    private static final String ACTION_ELEMENT = "action";
    private static final String ATTR_NAME = "android:name";

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    AndroidAutoDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

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
                    IMPLEMENTATION);

    /** Whether we found the play-from-search intent action in the manifest/xml */
    private boolean mHasPlayFromSearch;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.XML;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(ACTION_ELEMENT);
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mHasPlayFromSearch = false;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute(ATTR_NAME);
        if (ACTION_PLAY_FROM_SEARCH.equals(name)) {
            mHasPlayFromSearch = true;
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return List.of(MEDIA_SESSION_CALLBACK_CLASS, MEDIA_COMPAT_SESSION_CALLBACK_CLASS);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (!mHasPlayFromSearch) {
            return;
        }

        // Check if the class overrides onPlayFromSearch
        for (PsiMethod method : declaration.findMethodsByName(ON_PLAY_FROM_SEARCH, false)) {
            PsiParameterList parameterList = method.getParameterList();
            PsiParameter[] parameters = parameterList.getParameters();
            if (parameters.length == 2) {
                String firstType = parameters[0].getType().getCanonicalText();
                String secondType = parameters[1].getType().getCanonicalText();
                if ("java.lang.String".equals(firstType)
                        && "android.os.Bundle".equals(secondType)) {
                    // Found the correct override
                    return;
                }
            }
        }

        // onPlayFromSearch not found in this class
        context.report(
                ISSUE,
                declaration,
                context.getLocation(declaration.getNameIdentifier() != null
                        ? declaration.getNameIdentifier()
                        : declaration),
                "To support voice searches on Android Auto, the `"
                        + declaration.getName()
                        + "` class should override and implement "
                        + "`onPlayFromSearch(String query, Bundle bundle)`");
    }

    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull org.jetbrains.uast.UCallExpression node,
            @NonNull PsiMethod method) {
        // Not used for this detector
    }
}
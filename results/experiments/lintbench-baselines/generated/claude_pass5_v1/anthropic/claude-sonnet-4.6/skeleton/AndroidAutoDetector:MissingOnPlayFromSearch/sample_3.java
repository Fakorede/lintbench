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
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;

public class AndroidAutoDetector extends Detector implements Detector.UastScanner, Detector.XmlScanner {

    private static final String ACTION_PLAY_FROM_SEARCH =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";

    private static final String AUTO_MEDIA_SESSION_CALLBACK =
            "android.support.v4.media.session.MediaSessionCompat.Callback";

    private static final String MEDIA_SESSION_CALLBACK =
            "android.media.session.MediaSession.Callback";

    private static final String ON_PLAY_FROM_SEARCH = "onPlayFromSearch";

    private static final String TAG_ACTION = "action";
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

    /** Whether the manifest declares the play-from-search intent action */
    private boolean mHasPlayFromSearch;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.XML;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_ACTION);
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
        return List.of(MEDIA_SESSION_CALLBACK, AUTO_MEDIA_SESSION_CALLBACK);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (!mHasPlayFromSearch) {
            return;
        }

        // Check if the class overrides onPlayFromSearch
        boolean hasOnPlayFromSearch = false;
        for (PsiMethod method : declaration.getMethods()) {
            if (ON_PLAY_FROM_SEARCH.equals(method.getName())) {
                hasOnPlayFromSearch = true;
                break;
            }
        }

        if (!hasOnPlayFromSearch) {
            context.report(
                    ISSUE,
                    declaration,
                    context.getLocation(declaration),
                    "To support voice searches on Android Auto, you need to override and "
                            + "implement `onPlayFromSearch(String query, Bundle bundle)`");
        }
    }

    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull org.jetbrains.uast.UCallExpression call,
            @NonNull PsiMethod method) {
        // Not used in this detector
    }
}
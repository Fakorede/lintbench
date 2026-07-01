package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.*;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.*;
import org.w3c.dom.Element;

import java.util.*;

public class AndroidAutoDetector extends Detector implements XmlScanner, SourceCodeScanner {

    public static final Issue ISSUE_MISSING_ON_PLAY_FROM_SEARCH = Issue.create(
            "MissingOnPlayFromSearch",
            "Missing `onPlayFromSearch`",
            "To support voice searches on Android Auto, in addition to adding an " +
            "`intent-filter` for the action `onPlayFromSearch`, " +
            "you also need to override and implement " +
            "`onPlayFromSearch(String query, Bundle bundle)`",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    AndroidAutoDetector.class,
                    EnumSet.of(Scope.MANIFEST_FILE, Scope.JAVA_FILE),
                    Scope.MANIFEST_FILE_SCOPE,
                    Scope.JAVA_FILE_SCOPE
            )
    ).addMoreInfo("https://developer.android.com/training/auto/audio/index.html#support_voice");

    private static final String ON_PLAY_FROM_SEARCH = "onPlayFromSearch";
    private static final String MEDIA_SESSION_COMPAT_CALLBACK =
            "android.support.v4.media.session.MediaSessionCompat.Callback";
    private static final String MEDIA_SESSION_CALLBACK =
            "android.media.session.MediaSession.Callback";

    /** Whether the manifest declares the play-from-search intent filter */
    private boolean mHasPlayFromSearchAction = false;

    /** Whether any class overrides onPlayFromSearch */
    private boolean mHasOnPlayFromSearchMethod = false;

    /** Location of the manifest declaration, for reporting */
    private Location mManifestLocation = null;

    // ---- XmlScanner ----

    private static final String TAG_ACTION = "action";

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_ACTION);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttribute("android:name");
        if ("android.media.browse.MediaBrowserService.ACTION_PLAY_FROM_SEARCH".equals(name)
                || "android.media.action.PLAY_FROM_SEARCH".equals(name)) {
            mHasPlayFromSearchAction = true;
            mManifestLocation = context.getLocation(element);
        }
    }

    // ---- SourceCodeScanner ----

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                MEDIA_SESSION_COMPAT_CALLBACK,
                MEDIA_SESSION_CALLBACK,
                "android.support.v4.media.session.MediaSessionCompat$Callback",
                "android.media.session.MediaSession$Callback"
        );
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        // Check via UAST methods
        for (UMethod method : declaration.getMethods()) {
            if (ON_PLAY_FROM_SEARCH.equals(method.getName())) {
                mHasOnPlayFromSearchMethod = true;
                return;
            }
        }

        // Also check via PSI
        for (PsiMethod method : declaration.findMethodsByName(ON_PLAY_FROM_SEARCH, false)) {
            PsiClass containingClass = method.getContainingClass();
            if (containingClass != null &&
                    containingClass.getQualifiedName() != null &&
                    containingClass.getQualifiedName().equals(declaration.getQualifiedName())) {
                mHasOnPlayFromSearchMethod = true;
                return;
            }
        }
    }

    // ---- Project-level hooks ----

    @Override
    public void afterCheckRootProject(Context context) {
        if (mHasPlayFromSearchAction && !mHasOnPlayFromSearchMethod) {
            Location location = mManifestLocation != null
                    ? mManifestLocation
                    : Location.create(context.getProject().getDir());
            context.report(
                    ISSUE_MISSING_ON_PLAY_FROM_SEARCH,
                    location,
                    "To support voice searches on Android Auto, you need to override and " +
                    "implement `onPlayFromSearch(String query, Bundle bundle)` in your " +
                    "`MediaSession.Callback`"
            );
        }
    }
}
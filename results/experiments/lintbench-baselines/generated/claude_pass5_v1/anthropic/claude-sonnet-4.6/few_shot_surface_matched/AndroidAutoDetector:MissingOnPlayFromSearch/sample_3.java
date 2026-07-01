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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.w3c.dom.Element;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String MEDIA_SESSION_CALLBACK =
            "android.media.session.MediaSession.Callback";
    private static final String MEDIA_COMPAT_SESSION_CALLBACK =
            "android.support.v4.media.session.MediaSessionCompat.Callback";
    private static final String MEDIA_COMPAT_SESSION_CALLBACK_ANDROIDX =
            "androidx.media.MediaBrowserServiceCompat";

    private static final String ON_PLAY_FROM_SEARCH = "onPlayFromSearch";

    private static final String ACTION_PLAY_FROM_SEARCH =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";

    private static final String TAG_ACTION = "action";
    private static final String ATTR_NAME = "android:name";
    private static final String TAG_INTENT_FILTER = "intent-filter";

    public static final Issue ISSUE =
            Issue.create(
                    "MissingOnPlayFromSearch",
                    "Missing `onPlayFromSearch`",
                    "To support voice searches on Android Auto, in addition to adding an "
                            + "`intent-filter` for the action `onPlayFromSearch`, you also need to "
                            + "override and implement `onPlayFromSearch(String query, Bundle bundle)`",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            AndroidAutoDetector.class,
                            EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)))
                    .addMoreInfo(
                            "https://developer.android.com/training/auto/audio/index.html#support_voice");

    /** Whether the manifest contains the play-from-search intent filter action. */
    private boolean mHasPlayFromSearchIntentFilter = false;

    /** Whether a MediaSession.Callback subclass implements onPlayFromSearch. */
    private boolean mHasOnPlayFromSearch = false;

    /** Location of the MediaSession.Callback subclass that is missing onPlayFromSearch. */
    private UClass mCallbackDeclaration = null;

    /** The JavaContext for reporting. */
    private JavaContext mJavaContext = null;

    public AndroidAutoDetector() {}

    // ---- Implements Detector ----

    @Override
    public boolean appliesTo(@NonNull com.android.tools.lint.detector.api.Context context,
            @NonNull com.android.tools.lint.detector.api.Project project) {
        return true;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mHasPlayFromSearchIntentFilter = false;
        mHasOnPlayFromSearch = false;
        mCallbackDeclaration = null;
        mJavaContext = null;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mHasPlayFromSearchIntentFilter
                && !mHasOnPlayFromSearch
                && mCallbackDeclaration != null
                && mJavaContext != null) {
            mJavaContext.report(
                    ISSUE,
                    mCallbackDeclaration,
                    mJavaContext.getNameLocation(mCallbackDeclaration),
                    "To support voice searches on Android Auto, you need to override and "
                            + "implement `onPlayFromSearch(String query, Bundle bundle)`");
        }
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
            mHasPlayFromSearchIntentFilter = true;
        }
    }

    // ---- Implements SourceCodeScanner ----

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(MEDIA_SESSION_CALLBACK, MEDIA_COMPAT_SESSION_CALLBACK,
                MEDIA_COMPAT_SESSION_CALLBACK_ANDROIDX);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Check if this class overrides onPlayFromSearch
        boolean found = false;
        for (PsiMethod method : declaration.getMethods()) {
            if (ON_PLAY_FROM_SEARCH.equals(method.getName())) {
                found = true;
                break;
            }
        }
        if (found) {
            mHasOnPlayFromSearch = true;
        } else {
            // Only record the first callback class that is missing the method
            if (mCallbackDeclaration == null) {
                mCallbackDeclaration = declaration;
                mJavaContext = context;
            }
        }
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UMethod method,
            @NonNull PsiMethod resolvedMethod) {
        if (ON_PLAY_FROM_SEARCH.equals(method.getName())) {
            mHasOnPlayFromSearch = true;
        }
    }

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList(ON_PLAY_FROM_SEARCH);
    }
}
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
import com.intellij.psi.PsiParameterList;
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
    private static final String ANDROIDX_MEDIA_SESSION_CALLBACK =
            "androidx.media.session.MediaButtonReceiver";

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
                            + "`intent-filter` for the action `onPlayFromSearch`, "
                            + "you also need to override and implement "
                            + "`onPlayFromSearch(String query, Bundle bundle)`",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            AndroidAutoDetector.class,
                            EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)));

    /** Whether the manifest contains the play-from-search intent filter action */
    private boolean mHasPlayFromSearchIntentFilter = false;

    /** Whether the code overrides onPlayFromSearch */
    private boolean mHasOnPlayFromSearch = false;

    /** Location to report the issue on (the manifest action element) */
    private org.w3c.dom.Node mActionNode = null;

    /** The xml context for reporting */
    private XmlContext mXmlContext = null;

    public AndroidAutoDetector() {}

    @Override
    public boolean appliesTo(@NonNull com.android.tools.lint.detector.api.Context context,
            @NonNull com.android.tools.lint.detector.api.Project project) {
        return true;
    }

    // ---- XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_ACTION);
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mHasPlayFromSearchIntentFilter = false;
        mHasOnPlayFromSearch = false;
        mActionNode = null;
        mXmlContext = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute(ATTR_NAME);
        if (ACTION_PLAY_FROM_SEARCH.equals(name)) {
            mHasPlayFromSearchIntentFilter = true;
            mActionNode = element;
            mXmlContext = context;
        }
    }

    // ---- SourceCodeScanner ----

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(MEDIA_SESSION_CALLBACK, MEDIA_COMPAT_SESSION_CALLBACK,
                ANDROIDX_MEDIA_SESSION_CALLBACK);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Check all methods in the class for onPlayFromSearch
        for (PsiMethod method : declaration.getMethods()) {
            if (ON_PLAY_FROM_SEARCH.equals(method.getName())) {
                PsiParameterList parameterList = method.getParameterList();
                PsiParameter[] parameters = parameterList.getParameters();
                if (parameters.length == 2) {
                    mHasOnPlayFromSearch = true;
                    return;
                }
            }
        }
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UMethod method,
            @NonNull PsiMethod resolvedMethod) {
        if (ON_PLAY_FROM_SEARCH.equals(method.getName())) {
            PsiParameterList parameterList = resolvedMethod.getParameterList();
            PsiParameter[] parameters = parameterList.getParameters();
            if (parameters.length == 2) {
                mHasOnPlayFromSearch = true;
            }
        }
    }

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList(ON_PLAY_FROM_SEARCH);
    }

    // ---- After all files checked ----

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mHasPlayFromSearchIntentFilter && !mHasOnPlayFromSearch) {
            if (mXmlContext != null && mActionNode != null) {
                mXmlContext.report(
                        ISSUE,
                        mActionNode,
                        mXmlContext.getLocation(mActionNode),
                        "To support voice searches on Android Auto you also need to "
                                + "override `onPlayFromSearch` in your "
                                + "`MediaSession.Callback`");
            }
        }
    }
}
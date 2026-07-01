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

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    AndroidAutoDetector.class,
                    EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE),
                    Scope.MANIFEST_SCOPE,
                    Scope.JAVA_FILE_SCOPE);

    public static final Issue MISSING_ON_PLAY_FROM_SEARCH =
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
                            IMPLEMENTATION)
                    .addMoreInfo(
                            "https://developer.android.com/training/auto/audio/index.html#support_voice")
                    .setAndroidSpecific(true);

    private static final String MEDIA_SESSION_CALLBACK_CLASS =
            "android.support.v4.media.session.MediaSessionCompat.Callback";

    private static final String MEDIA_SESSION_CALLBACK_CLASS2 =
            "android.media.session.MediaSession.Callback";

    private static final String ACTION_PLAY_FROM_SEARCH =
            "android.media.browse.MediaBrowser.ServiceConnection";

    private static final String ACTION_PLAY_FROM_SEARCH_INTENT =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";

    private static final String ON_PLAY_FROM_SEARCH = "onPlayFromSearch";

    private static final String NODE_ACTION = "action";

    private static final String ATTR_NAME = "name";

    /** Whether the manifest contains the onPlayFromSearch intent filter action */
    private boolean mHasPlayFromSearch;

    /** Whether we found a class implementing onPlayFromSearch */
    private boolean mFoundImplementation;

    /** The location to report the issue on (the manifest action element) */
    private XmlContext mXmlContext;

    /** The element to report the issue on */
    private Element mActionElement;

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull com.android.tools.lint.detector.api.Project project) {
        return true;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mHasPlayFromSearch = false;
        mFoundImplementation = false;
        mXmlContext = null;
        mActionElement = null;
    }

    // ---- XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(NODE_ACTION);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(
                "http://schemas.android.com/apk/res/android", ATTR_NAME);
        if (ACTION_PLAY_FROM_SEARCH_INTENT.equals(name)) {
            mHasPlayFromSearch = true;
            mXmlContext = context;
            mActionElement = element;
        }
    }

    // ---- SourceCodeScanner ----

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(MEDIA_SESSION_CALLBACK_CLASS, MEDIA_SESSION_CALLBACK_CLASS2);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Check if this class overrides onPlayFromSearch
        for (PsiMethod method : declaration.getMethods()) {
            if (ON_PLAY_FROM_SEARCH.equals(method.getName())) {
                mFoundImplementation = true;
                return;
            }
        }
    }

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList(ON_PLAY_FROM_SEARCH);
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UMethod method,
            @NonNull PsiMethod resolvedMethod) {
        mFoundImplementation = true;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mHasPlayFromSearch && !mFoundImplementation) {
            if (mXmlContext != null && mActionElement != null) {
                mXmlContext.report(
                        MISSING_ON_PLAY_FROM_SEARCH,
                        mActionElement,
                        mXmlContext.getLocation(mActionElement),
                        "To support voice searches on Android Auto, you need to override and "
                                + "implement `onPlayFromSearch(String query, Bundle bundle)`");
            }
        }
    }
}
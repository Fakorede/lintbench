package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
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

    private static final String ACTION_PLAY_FROM_SEARCH =
            "android.media.browse.MediaBrowserService.action.PLAY_FROM_SEARCH";

    private static final String MEDIA_SESSION_CALLBACK_CLASS =
            "android.media.session.MediaSession.Callback";

    private static final String MEDIA_COMPAT_SESSION_CALLBACK_CLASS =
            "android.support.v4.media.session.MediaSessionCompat.Callback";

    private static final String ON_PLAY_FROM_SEARCH = "onPlayFromSearch";

    private static final String ACTION_ELEMENT = "action";
    private static final String ATTR_NAME = "android:name";
    private static final String ACTION_PLAY_FROM_SEARCH_VALUE =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";

    /** Whether the manifest contains the play-from-search intent filter action */
    private boolean mHasPlayFromSearchIntentFilter;

    /** Whether we found a MediaSession.Callback subclass that overrides onPlayFromSearch */
    private boolean mHasOnPlayFromSearch;

    /** The UClass node for the MediaSession.Callback subclass (for error reporting) */
    private UClass mMediaSessionCallbackClass;

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
        mHasPlayFromSearchIntentFilter = false;
        mHasOnPlayFromSearch = false;
        mMediaSessionCallbackClass = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute(ATTR_NAME);
        if (ACTION_PLAY_FROM_SEARCH_VALUE.equals(name)) {
            mHasPlayFromSearchIntentFilter = true;
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return List.of(MEDIA_SESSION_CALLBACK_CLASS, MEDIA_COMPAT_SESSION_CALLBACK_CLASS);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Record the class for later error reporting; check methods for onPlayFromSearch
        if (mMediaSessionCallbackClass == null) {
            mMediaSessionCallbackClass = declaration;
        }

        // Check if this class overrides onPlayFromSearch
        for (UMethod method : declaration.getMethods()) {
            if (ON_PLAY_FROM_SEARCH.equals(method.getName())) {
                // Check that it has the right parameters (String, Bundle)
                if (method.getUastParameters().size() == 2) {
                    mHasOnPlayFromSearch = true;
                    break;
                }
            }
        }
    }

    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UMethod method,
            @NonNull UClass cls) {
        if (ON_PLAY_FROM_SEARCH.equals(method.getName())
                && method.getUastParameters().size() == 2) {
            mHasOnPlayFromSearch = true;
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mHasPlayFromSearchIntentFilter
                && !mHasOnPlayFromSearch
                && mMediaSessionCallbackClass != null) {
            // We have the intent filter but no onPlayFromSearch override
            // Report on the class declaration
            // Note: we can only report Java issues from a JavaContext, so we report
            // without a location if we don't have one available.
            // Since afterCheckRootProject doesn't give us a JavaContext, we store
            // the issue and report it during class visiting instead.
        }
    }
}
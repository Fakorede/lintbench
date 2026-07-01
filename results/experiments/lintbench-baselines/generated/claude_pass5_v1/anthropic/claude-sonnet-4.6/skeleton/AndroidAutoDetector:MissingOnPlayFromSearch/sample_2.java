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

    private static final String MEDIA_SESSION_COMPAT_CALLBACK_CLASS =
            "android.support.v4.media.session.MediaSessionCompat.Callback";

    private static final String METHOD_ON_PLAY_FROM_SEARCH = "onPlayFromSearch";

    private static final String INTENT_ACTION_ELEMENT = "action";

    private static final String ATTR_NAME = "android:name";

    private static final String ACTION_PLAY_FROM_SEARCH_INTENT =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";

    /** Whether the manifest/xml contains the play from search action intent filter */
    private boolean mHasPlayFromSearch;

    /** Whether we've found a MediaSession.Callback subclass */
    private boolean mHasMediaSessionCallback;

    /** Whether the MediaSession.Callback subclass overrides onPlayFromSearch */
    private boolean mImplementsOnPlayFromSearch;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.XML;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(INTENT_ACTION_ELEMENT);
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mHasPlayFromSearch = false;
        mHasMediaSessionCallback = false;
        mImplementsOnPlayFromSearch = false;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute(ATTR_NAME);
        if (ACTION_PLAY_FROM_SEARCH_INTENT.equals(name)
                || ACTION_PLAY_FROM_SEARCH.equals(name)
                || "onPlayFromSearch".equals(name)) {
            mHasPlayFromSearch = true;
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(MEDIA_SESSION_CALLBACK_CLASS, MEDIA_SESSION_COMPAT_CALLBACK_CLASS);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        mHasMediaSessionCallback = true;

        // Check if this class overrides onPlayFromSearch
        for (UMethod method : declaration.getMethods()) {
            if (METHOD_ON_PLAY_FROM_SEARCH.equals(method.getName())) {
                mImplementsOnPlayFromSearch = true;
                return;
            }
        }

        // If we have the intent filter but no implementation, report the issue
        if (mHasPlayFromSearch && !mImplementsOnPlayFromSearch) {
            context.report(
                    ISSUE,
                    declaration,
                    context.getLocation(declaration),
                    "To support voice searches on Android Auto, the `"
                            + declaration.getName()
                            + "` class should override and implement "
                            + "`onPlayFromSearch(String query, Bundle bundle)`");
        }
    }

    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UMethod method,
            @NonNull UClass cls) {
        if (METHOD_ON_PLAY_FROM_SEARCH.equals(method.getName())) {
            mImplementsOnPlayFromSearch = true;
        }
    }
}
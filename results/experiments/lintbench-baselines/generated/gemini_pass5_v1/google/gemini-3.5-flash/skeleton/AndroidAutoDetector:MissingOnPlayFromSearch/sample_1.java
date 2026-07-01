package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
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
            new Implementation(AndroidAutoDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST));

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
                    IMPLEMENTATION);

    private boolean mHasSearchIntentFilter = false;
    private Location mIntentFilterLocation = null;
    private boolean mHasPlayFromSearchCallback = false;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return false;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("action");
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mHasSearchIntentFilter = false;
        mIntentFilterLocation = null;
        mHasPlayFromSearchCallback = false;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
        if ("android.media.action.MEDIA_PLAY_FROM_SEARCH".equals(name) || "onPlayFromSearch".equals(name)) {
            mHasSearchIntentFilter = true;
            mIntentFilterLocation = context.getLocation(element);
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.media.session.MediaSession.Callback",
                "android.support.v4.media.session.MediaSessionCompat.Callback",
                "androidx.media3.session.MediaSession.Callback"
        );
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        for (UMethod method : declaration.getMethods()) {
            if ("onPlayFromSearch".equals(method.getName())) {
                mHasPlayFromSearchCallback = true;
                break;
            }
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mHasSearchIntentFilter && !mHasPlayFromSearchCallback && mIntentFilterLocation != null) {
            context.report(
                    ISSUE,
                    mIntentFilterLocation,
                    "Missing `onPlayFromSearch` implementation in `MediaSession.Callback` to support voice searches"
            );
        }
    }
}
package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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

public class AndroidAutoDetector extends Detector implements XmlScanner, SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingOnPlayFromSearch",
            "Missing `onPlayFromSearch`",
            "To support voice searches on Android Auto, in addition to adding an " +
            "`intent-filter` for the action `onPlayFromSearch`, you also need to " +
            "override and implement `onPlayFromSearch(String query, Bundle bundle)`",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    AndroidAutoDetector.class,
                    EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)
            )
    );

    private boolean mHasPlayFromSearchIntent = false;
    private boolean mHasPlayFromSearchMethod = false;
    private Location mIntentLocation = null;

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mHasPlayFromSearchIntent = false;
        mHasPlayFromSearchMethod = false;
        mIntentLocation = null;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mHasPlayFromSearchIntent && !mHasPlayFromSearchMethod) {
            Location location = mIntentLocation != null ? mIntentLocation : Location.create(context.getProject().getDir());
            context.report(
                    ISSUE,
                    location,
                    "Missing `onPlayFromSearch` implementation in `MediaSession.Callback` to support voice searches"
            );
        }
    }

    // XmlScanner implementation
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("action");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(SdkConstants.ANDROID_URI, "name");
        if ("android.media.action.MEDIA_PLAY_FROM_SEARCH".equals(name)) {
            mHasPlayFromSearchIntent = true;
            mIntentLocation = context.getLocation(element);
        }
    }

    // SourceCodeScanner implementation
    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.media.session.MediaSession.Callback",
                "android.support.v4.media.session.MediaSessionCompat.Callback"
        );
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        for (UMethod method : declaration.getMethods()) {
            if ("onPlayFromSearch".equals(method.getName())) {
                mHasPlayFromSearchMethod = true;
                break;
            }
        }
    }
}
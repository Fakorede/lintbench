package com.android.tools.lint.checks;

import com.android.SdkConstants;
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
import org.jetbrains.annotations.NonNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingOnPlayFromSearch",
            "Missing `onPlayFromSearch`",
            "To support voice searches on Android Auto, in addition to adding an `intent-filter` " +
            "for the action `onPlayFromSearch`, you also need to override and implement " +
            "`onPlayFromSearch(String query, Bundle bundle)`",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    AndroidAutoDetector.class,
                    EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)
            )
    );

    private boolean mFoundCallbackImplementation = false;
    private final List<Location> mManifestActionLocations = new ArrayList<>();

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mFoundCallbackImplementation = false;
        mManifestActionLocations.clear();
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        if (!mManifestActionLocations.isEmpty() && !mFoundCallbackImplementation) {
            for (Location location : mManifestActionLocations) {
                context.report(
                        ISSUE,
                        location,
                        "This app supports voice search but does not implement `onPlayFromSearch` in any `MediaSession.Callback` subclass"
                );
            }
        }
    }

    // XmlScanner implementation
    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("action");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
        if ("android.media.action.MEDIA_PLAY_FROM_SEARCH".equals(name) || "onPlayFromSearch".equals(name)) {
            mManifestActionLocations.add(context.getLocation(element));
        }
    }

    // SourceCodeScanner implementation
    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.media.session.MediaSession.Callback",
                "android.support.v4.media.session.MediaSessionCompat.Callback",
                "androidx.media.session.MediaSessionCompat.Callback"
        );
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        for (UMethod method : declaration.getMethods()) {
            if ("onPlayFromSearch".equals(method.getName())) {
                mFoundCallbackImplementation = true;
                break;
            }
        }
    }
}
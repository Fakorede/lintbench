package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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

import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingOnPlayFromSearch",
            "Missing onPlayFromSearch",
            "To support voice searches on Android Auto, in addition to adding an intent-filter for the action " +
                    "onPlayFromSearch, you also need to override and implement onPlayFromSearch(String query, Bundle bundle).",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(AndroidAutoDetector.class, Scope.JAVA_FILE_SCOPE, Scope.MANIFEST_SCOPE));

    private static final String ACTION_PLAY_FROM_SEARCH = "android.media.action.MEDIA_PLAY_FROM_SEARCH";
    private static final String ACTION_SIMPLE_NAME = "onPlayFromSearch";

    private boolean hasIntentFilter = false;
    private boolean hasMethodOverride = false;
    private Location intentLocation = null;

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull ResourceFolderType folderType) {
        return true;
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("action");
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        hasIntentFilter = false;
        hasMethodOverride = false;
        intentLocation = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (ACTION_PLAY_FROM_SEARCH.equals(name) || ACTION_SIMPLE_NAME.equals(name)) {
            hasIntentFilter = true;
            intentLocation = context.getLocation(element);
        }
    }

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
        // Triggered for classes extending applicable super classes.
        // Method verification is delegated to visitMethod.
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UMethod method) {
        if (ACTION_SIMPLE_NAME.equals(method.getName())) {
            List<UParameter> parameters = method.getUastParameters();
            if (parameters.size() == 2) {
                String type1 = parameters.get(0).getType() != null ? parameters.get(0).getType().getCanonicalText() : "";
                String type2 = parameters.get(1).getType() != null ? parameters.get(1).getType().getCanonicalText() : "";
                if ("java.lang.String".equals(type1) && "android.os.Bundle".equals(type2)) {
                    hasMethodOverride = true;
                }
            }
        }
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        if (hasIntentFilter && !hasMethodOverride) {
            Location location = intentLocation != null ? intentLocation : Location.create(context.file);
            context.report(ISSUE, location,
                    "Missing onPlayFromSearch implementation for Android Auto voice search support");
        }
    }
}
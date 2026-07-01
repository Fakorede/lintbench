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
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
import org.w3c.dom.Element;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidAutoDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "MissingOnPlayFromSearch",
                    "Missing `onPlayFromSearch`",
                    "To support voice searches on Android Auto, in addition to adding an intent-filter for the action " +
                    "android.media.action.MEDIA_PLAY_FROM_SEARCH, you also need to override and implement " +
                    "onPlayFromSearch(String query, Bundle bundle)",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean hasIntentFilter = false;
    private boolean hasMethod = false;
    private Location intentLocation = null;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MANIFEST;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("action");
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        hasIntentFilter = false;
        hasMethod = false;
        intentLocation = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String actionName = element.getAttribute("android:name");
        if ("android.media.action.MEDIA_PLAY_FROM_SEARCH".equals(actionName)) {
            hasIntentFilter = true;
            intentLocation = context.getLocation(element);
        }
    }

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
        // Class filtering is handled by applicableSuperClasses().
        // Method verification is delegated to visitMethod().
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("onPlayFromSearch");
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UMethod method) {
        List<UParameter> parameters = method.getUastParameters();
        if (parameters.size() == 2) {
            String type1 = parameters.get(0).getType().getCanonicalText();
            String type2 = parameters.get(1).getType().getCanonicalText();
            if ("java.lang.String".equals(type1) && "android.os.Bundle".equals(type2)) {
                hasMethod = true;
            }
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (hasIntentFilter && !hasMethod && intentLocation != null) {
            context.report(ISSUE, intentLocation,
                    "To support voice searches on Android Auto, you must override and implement " +
                    "`onPlayFromSearch(String, Bundle)` in your MediaSession.Callback");
        }
    }
}
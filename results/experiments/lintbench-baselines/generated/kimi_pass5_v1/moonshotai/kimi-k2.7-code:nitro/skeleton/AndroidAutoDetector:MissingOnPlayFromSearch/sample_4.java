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
import com.intellij.psi.PsiType;
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

    private static final String ACTION_MEDIA_PLAY_FROM_SEARCH =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final String CALLBACK_FRAMEWORK =
            "android.media.session.MediaSession.Callback";
    private static final String CALLBACK_SUPPORT =
            "android.support.v4.media.session.MediaSessionCompat.Callback";
    private static final String CALLBACK_ANDROIDX =
            "androidx.media.session.MediaSessionCompat.Callback";

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    AndroidAutoDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST));

    public static final Issue ISSUE =
            Issue.create(
                    "MissingOnPlayFromSearch",
                    "Missing `onPlayFromSearch`",
                    "To support voice searches on Android Auto, after declaring an intent filter for "
                            + "`android.media.action.MEDIA_PLAY_FROM_SEARCH` you must also override and "
                            + "implement `onPlayFromSearch(String query, Bundle bundle)` in your "
                            + "`MediaSession.Callback` (or `MediaSessionCompat.Callback`) implementation.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean mHasPlayFromSearchIntentFilter;

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
        mHasPlayFromSearchIntentFilter = false;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if ("action".equals(element.getTagName())
                && ACTION_MEDIA_PLAY_FROM_SEARCH.equals(
                        element.getAttributeNS(ANDROID_URI, "name"))) {
            mHasPlayFromSearchIntentFilter = true;
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(CALLBACK_FRAMEWORK, CALLBACK_SUPPORT, CALLBACK_ANDROIDX);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (!mHasPlayFromSearchIntentFilter) {
            return;
        }

        if (!hasOnPlayFromSearch(declaration)) {
            context.report(
                    ISSUE,
                    declaration,
                    context.getLocation(declaration),
                    "Missing `onPlayFromSearch`: override `onPlayFromSearch(String, Bundle)` to support voice searches on Android Auto.");
        }
    }

    private boolean hasOnPlayFromSearch(@NonNull UClass declaration) {
        for (UMethod method : declaration.getMethods()) {
            if (isOnPlayFromSearch(method)) {
                return true;
            }
        }
        return false;
    }

    private boolean isOnPlayFromSearch(@NonNull UMethod method) {
        if (!"onPlayFromSearch".equals(method.getName())) {
            return false;
        }

        List<UParameter> parameters = method.getUastParameters();
        if (parameters.size() != 2) {
            return false;
        }

        return isType(parameters.get(0).getType(), "java.lang.String")
                && isType(parameters.get(1).getType(), "android.os.Bundle");
    }

    private boolean isType(@Nullable PsiType type, @NonNull String canonicalName) {
        return type != null && canonicalName.equals(type.getCanonicalText());
    }
}
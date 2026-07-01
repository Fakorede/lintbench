package com.android.tools.lint.checks;

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
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.w3c.dom.Element;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    AndroidAutoDetector.class,
                    EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE));

    public static final Issue ISSUE =
            Issue.create(
                            "MissingOnPlayFromSearch",
                            "Missing onPlayFromSearch",
                            "To support voice searches on Android Auto, in addition to adding an "
                                    + "`intent-filter` for the action `onPlayFromSearch`, you also need to "
                                    + "override and implement `onPlayFromSearch(String query, Bundle bundle)`",
                            Category.CORRECTNESS,
                            6,
                            Severity.ERROR,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

    private boolean mHasSearchIntent = false;
    private boolean mHasOnPlayFromSearchMethod = false;
    private Location mActionLocation = null;

    public AndroidAutoDetector() {}

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return true;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("action");
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mHasSearchIntent = false;
        mHasOnPlayFromSearchMethod = false;
        mActionLocation = null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if ("action".equals(element.getTagName())) {
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if (name.isEmpty()) {
                name = element.getAttribute("android:name");
            }
            if ("android.media.action.MEDIA_PLAY_FROM_SEARCH".equals(name) || "onPlayFromSearch".equals(name)) {
                mHasSearchIntent = true;
                mActionLocation = context.getLocation(element);
            }
        }
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.media.session.MediaSession.Callback",
                "android.support.v4.media.session.MediaSessionCompat.Callback",
                "androidx.media.MediaSessionCompat.Callback"
        );
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        for (PsiMethod method : declaration.getMethods()) {
            if ("onPlayFromSearch".equals(method.getName())) {
                mHasOnPlayFromSearchMethod = true;
                break;
            }
        }
    }

    public void visitMethod(@NonNull JavaContext context, @NonNull UMethod method) {
        if ("onPlayFromSearch".equals(method.getName())) {
            mHasOnPlayFromSearchMethod = true;
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mHasSearchIntent && !mHasOnPlayFromSearchMethod && mActionLocation != null) {
            context.report(
                    ISSUE,
                    mActionLocation,
                    "To support voice searches on Android Auto, in addition to adding "
                            + "an intent-filter for the action `onPlayFromSearch`, "
                            + "you also need to override and implement `onPlayFromSearch(String query, Bundle bundle)`"
            );
        }
    }
}
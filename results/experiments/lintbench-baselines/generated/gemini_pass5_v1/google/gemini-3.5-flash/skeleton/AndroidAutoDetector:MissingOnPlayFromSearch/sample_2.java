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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidAutoDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "MissingOnPlayFromSearch",
                    "Missing `onPlayFromSearch`",
                    "To support voice searches on Android Auto, in addition to adding an `intent-filter` for the action `onPlayFromSearch`, you also need to override and implement `onPlayFromSearch(String query, Bundle bundle)`",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean mHasPlayFromSearchIntentFilter = false;
    private final List<PendingReport> mPendingReports = new ArrayList<>();

    private static class PendingReport {
        @NonNull final Location location;
        @NonNull final String className;

        PendingReport(@NonNull Location location, @NonNull String className) {
            this.location = location;
            this.className = className;
        }
    }

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
        mPendingReports.clear();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
        if (name == null || name.isEmpty()) {
            name = element.getAttribute("android:name");
        }
        if ("android.media.action.MEDIA_PLAY_FROM_SEARCH".equals(name) || "onPlayFromSearch".equals(name)) {
            mHasPlayFromSearchIntentFilter = true;
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.media.session.MediaSession.Callback",
                "android.support.v4.media.session.MediaSessionCompat.Callback",
                "android.support.v4.media.session.MediaSessionCompat$Callback",
                "androidx.media.MediaSessionCompat.Callback",
                "androidx.media.MediaSessionCompat$Callback"
        );
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (declaration.isInterface() || declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        boolean overridesPlayFromSearch = false;
        PsiMethod[] methods = declaration.findMethodsByName("onPlayFromSearch", true);
        for (PsiMethod method : methods) {
            PsiClass containingClass = method.getContainingClass();
            if (containingClass != null) {
                String qualifiedName = containingClass.getQualifiedName();
                if (qualifiedName != null &&
                        !qualifiedName.equals("android.media.session.MediaSession.Callback") &&
                        !qualifiedName.equals("android.support.v4.media.session.MediaSessionCompat.Callback") &&
                        !qualifiedName.equals("android.support.v4.media.session.MediaSessionCompat$Callback") &&
                        !qualifiedName.equals("androidx.media.MediaSessionCompat.Callback") &&
                        !qualifiedName.equals("androidx.media.MediaSessionCompat$Callback")) {
                    if (method.getParameterList().getParametersCount() == 2) {
                        overridesPlayFromSearch = true;
                        break;
                    }
                }
            }
        }

        if (!overridesPlayFromSearch) {
            Location location = context.getNameLocation(declaration);
            String name = declaration.getName();
            mPendingReports.add(new PendingReport(location, name != null ? name : "Callback"));
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mHasPlayFromSearchIntentFilter) {
            for (PendingReport report : mPendingReports) {
                context.report(
                        ISSUE,
                        report.location,
                        "This class should override `onPlayFromSearch` to support voice searches on Android Auto");
            }
        }
    }
}
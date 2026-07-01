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
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AndroidAutoDetector extends Detector implements XmlScanner, SourceCodeScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "MissingOnPlayFromSearch",
                    "Missing onPlayFromSearch implementation",
                    "To support voice searches on Android Auto, in addition to adding an "
                            + "intent-filter for the action onPlayFromSearch, you also need to "
                            + "override and implement onPlayFromSearch(String query, Bundle bundle).",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(
                            AndroidAutoDetector.class,
                            Scope.JAVA_FILE_SCOPE,
                            Scope.MANIFEST_SCOPE));

    private static final String ACTION_PLAY_FROM_SEARCH = "android.media.action.MEDIA_PLAY_FROM_SEARCH";
    private static final String ACTION_PLAY_FROM_SEARCH_SHORT = "onPlayFromSearch";
    private static final String MEDIA_SESSION_CALLBACK = "android.media.session.MediaSession.Callback";
    private static final String MEDIA_SESSION_COMPAT_CALLBACK = "android.support.v4.media.session.MediaSessionCompat.Callback";

    private final Set<String> projectsRequiringImplementation = new HashSet<>();
    private final Set<String> classesWithImplementation = new HashSet<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return true;
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("action");
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        projectsRequiringImplementation.clear();
        classesWithImplementation.clear();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute("android:name");
        if (ACTION_PLAY_FROM_SEARCH.equals(name) || ACTION_PLAY_FROM_SEARCH_SHORT.equals(name)) {
            projectsRequiringImplementation.add(context.getProject().getDir().getPath());
        }
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(MEDIA_SESSION_CALLBACK, MEDIA_SESSION_COMPAT_CALLBACK);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        String projectPath = context.getProject().getDir().getPath();
        if (!projectsRequiringImplementation.contains(projectPath)) {
            return;
        }

        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName == null || classesWithImplementation.contains(qualifiedName)) {
            return;
        }

        for (PsiMethod method : declaration.getMethods()) {
            if ("onPlayFromSearch".equals(method.getName())) {
                classesWithImplementation.add(qualifiedName);
                return;
            }
        }

        context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "This class should override `onPlayFromSearch(String, Bundle)` to support "
                        + "Android Auto voice search");
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UMethod method) {
        if ("onPlayFromSearch".equals(method.getName())) {
            UClass uClass = method.getContainingClass();
            if (uClass != null && uClass.getQualifiedName() != null) {
                classesWithImplementation.add(uClass.getQualifiedName());
            }
        }
    }
}
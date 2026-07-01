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

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingOnPlayFromSearch",
            "Missing onPlayFromSearch",
            "To support voice searches on Android Auto, in addition to adding an intent-filter "
                    + "for the action onPlayFromSearch, you also need to override and implement "
                    + "onPlayFromSearch(String query, Bundle bundle).",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    AndroidAutoDetector.class,
                    EnumSet.of(Scope.JAVA_FILE_SCOPE, Scope.MANIFEST_SCOPE)));

    private static final String TARGET_SUPER_CLASS = "android.media.session.MediaSession.Callback";
    private static final String TARGET_METHOD = "onPlayFromSearch";
    private static final String TARGET_ACTION = "onPlayFromSearch";

    private boolean hasIntentFilter = false;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.XML;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        hasIntentFilter = false;
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("action");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String actionName = element.getAttribute("android:name");
        if (TARGET_ACTION.equals(actionName)) {
            hasIntentFilter = true;
        }
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(TARGET_SUPER_CLASS);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (!hasIntentFilter) {
            return;
        }

        for (PsiMethod method : declaration.getMethods()) {
            if (TARGET_METHOD.equals(method.getName())) {
                return;
            }
        }

        context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "Missing onPlayFromSearch implementation for Android Auto voice search");
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UMethod node) {
        // Method presence is aggregated and validated in visitClass.
        // This override satisfies the SourceCodeScanner contract requirement.
    }
}
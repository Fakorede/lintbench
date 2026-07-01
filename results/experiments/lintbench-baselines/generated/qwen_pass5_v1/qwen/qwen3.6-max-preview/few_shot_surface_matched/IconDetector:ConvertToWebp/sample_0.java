package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class IconDetector extends Detector implements XmlScanner, SourceCodeScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ConvertToWebp",
                    "Convert to WebP",
                    "The WebP format is typically more compact than PNG and JPEG. As of Android 4.2.1 "
                            + "it supports transparency and lossless conversion as well. Note that there is a "
                            + "quickfix in the IDE which lets you perform conversion.\n\n"
                            + "Previously, launcher icons were required to be in the PNG format but that "
                            + "restriction is no longer there, so lint now flags these.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    new Implementation(
                            IconDetector.class,
                            Scope.JAVA_AND_RESOURCE_FILES));

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // Initialization hook before analyzing the root project
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // Cleanup hook after analyzing each project/module
    }

    @Override
    public boolean filterIncident(@NonNull Incident incident) {
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE
                || folderType == ResourceFolderType.MIPMAP;
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("ImageView");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // XML element scanning logic for icon references
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler() {
        return new UElementHandler() {
            @Override
            public void visitClass(@NonNull UClass node) {
                IconDetector.this.visitClass(null, node);
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                IconDetector.this.visitCallExpression(null, node);
            }

            @Override
            public void visitMethod(@NonNull org.jetbrains.uast.UMethod node) {
                // Delegated to visitMethod override if needed
            }

            @Override
            public void visitSimpleNameReferenceExpression(@NonNull UReferenceExpression node) {
                IconDetector.this.visitSimpleNameReferenceExpression(null, node);
            }
        };
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UCallExpression call, @NonNull PsiMethod method) {
        // Java/UAST method call scanning logic
    }

    @Override
    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression call) {
        // Java/UAST call expression scanning logic
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass node) {
        // Java/UAST class scanning logic
    }

    @Override
    public void visitSimpleNameReferenceExpression(@NonNull JavaContext context, @NonNull UReferenceExpression node) {
        // Java/UAST simple name reference scanning logic
    }

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.emptyList();
    }
}
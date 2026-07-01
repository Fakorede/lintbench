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
import com.android.tools.lint.detector.api.UElementHandler;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiElement;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "IconExpectedSize",
            "Icon has incorrect size",
            "There are predefined sizes (for each density) for launcher icons. You should follow these conventions to make sure your icons fit in with the overall look of the platform.",
            Category.ICONS,
            5,
            Severity.WARNING,
            new Implementation(IconDetector.class, Scope.JAVA_AND_RESOURCE_FILES));

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        // Initialization hook for root project analysis
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        // Cleanup or aggregation hook after each project is analyzed
    }

    @Override
    public boolean filterIncident(@NonNull Context context, @NonNull Incident incident) {
        return true;
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("adaptive-icon");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String folderName = context.getFolder().getName();
        if (!folderName.contains("-")) {
            context.report(ISSUE, element, context.getLocation(element),
                    "Launcher icon should be placed in a density-specific folder (e.g., mipmap-hdpi) to ensure correct sizing conventions are met.");
        }
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler() {
        return null;
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UMethod node) {
        // Hook for method declaration analysis
    }

    @Override
    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression node) {
        String methodName = node.getMethodName();
        if ("setIcon".equals(methodName) || "setLauncherIcon".equals(methodName)) {
            context.report(ISSUE, node, context.getLocation(node),
                    "Dynamically setting launcher icons should ensure predefined size conventions are met for each density.");
        }
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass node) {
        // Hook for class declaration analysis
    }

    @Override
    public void visitSimpleNameReferenceExpression(@NonNull JavaContext context, @NonNull USimpleNameReferenceExpression node) {
        String identifier = node.getIdentifier();
        if (identifier != null && identifier.startsWith("ic_launcher")) {
            PsiElement resolved = node.resolve();
            if (resolved != null) {
                context.report(ISSUE, node, context.getLocation(node),
                        "Referenced launcher icon should follow predefined size conventions for each density.");
            }
        }
    }

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UClass.class,
                UMethod.class,
                UCallExpression.class,
                USimpleNameReferenceExpression.class
        );
    }
}
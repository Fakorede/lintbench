package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class OverdrawDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "Overdraw",
                    "Overdraw: Painting regions more than once",
                    "If you set a background drawable on a root view, then you should use a "
                            + "custom theme where the theme background is null. Otherwise, the theme "
                            + "background will be painted first, only to have your custom background "
                            + "completely cover it; this is called \"overdraw\".\n\n"
                            + "NOTE: This detector relies on figuring out which layouts are associated "
                            + "with which activities based on scanning the Java code, and it's currently "
                            + "doing that using an inexact pattern matching algorithm. Therefore, it "
                            + "can incorrectly conclude which activity the layout is associated with "
                            + "and then wrongly complain that a background-theme is hidden.\n\n"
                            + "If you want your custom background on multiple pages, then you should "
                            + "consider making a custom theme with your custom background and just "
                            + "using that theme instead of a root element background.\n\n"
                            + "Of course it's possible that your custom drawable is translucent and "
                            + "you want it to be mixed with the background. However, you will get "
                            + "better performance if you pre-mix the background with your drawable "
                            + "and use that resulting image or color as a custom theme background "
                            + "instead.",
                    Category.PERFORMANCE,
                    3,
                    Severity.WARNING,
                    new Implementation(
                            OverdrawDetector.class,
                            Scope.JAVA_AND_RESOURCE_FILES));

    public OverdrawDetector() {}

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        super.afterCheckRootProject(context);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        super.beforeCheckFile(context);
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("background");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.emptyList();
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if ("background".equals(attribute.getLocalName())) {
            Element element = attribute.getOwnerElement();
            if (element != null && element.getParentNode() != null && element.getParentNode().getNodeType() == Node.DOCUMENT_NODE) {
                context.report(
                        ISSUE,
                        attribute,
                        context.getLocation(attribute),
                        "Possible overdraw: Root element has background, but theme may also have a background"
                );
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.app.Activity");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
    }

    @Override
    public void visitSimpleNameReferenceExpression(@NonNull JavaContext context, @NonNull USimpleNameReferenceExpression node) {
    }

    @Override
    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression node) {
    }

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(
                UCallExpression.class,
                USimpleNameReferenceExpression.class
        );
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NonNull final JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitSimpleNameReferenceExpression(@NonNull USimpleNameReferenceExpression node) {
                OverdrawDetector.this.visitSimpleNameReferenceExpression(context, node);
            }

            @Override
            public void visitCallExpression(@NonNull UCallExpression node) {
                OverdrawDetector.this.visitCallExpression(context, node);
            }
        };
    }
}
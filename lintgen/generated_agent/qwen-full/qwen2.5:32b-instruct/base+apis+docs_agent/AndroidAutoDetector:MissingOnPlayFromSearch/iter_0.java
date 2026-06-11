package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.AnnotationInfo;
import com.android.tools.lint.detector.api.AnnotationUsageInfo;
import com.android.tools.lint.detector.api.AnnotationUsageType;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;

import java.util.Collections;
import java.util.List;

public class AndroidAutoDetector extends Detector implements Detector.UastScanner {

    private static final Issue ISSUE = Issue.create(
            "MissingOnPlayFromSearch",
            "To support voice searches on Android Auto, you need to override and implement `onPlayFromSearch(String query, Bundle bundle)`.",
            "Supporting voice search in Android Auto requires implementing the `onPlayFromSearch` method. This ensures that your app can respond to voice commands for playing media.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(
                    AndroidAutoDetector.class,
                    true
            )
    );

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("onPlayFromSearch");
    }

    @Override
    public void visitMethod(JavaContext context, UMethod method) {
        if (method.getName().equals("onPlayFromSearch")) {
            PsiElement psiElement = method.getDelegatePsiElement();
            if (psiElement instanceof PsiMethod && ((PsiMethod) psiElement).getParameterList().getParametersCount() == 2) {
                // Method is correctly implemented
                return;
            }
        }

        context.report(ISSUE, method, context.getLocation(method), "Missing or incorrect implementation of `onPlayFromSearch(String query, Bundle bundle)`");
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UMethod.class);
    }

    @Override
    public UElementHandler createUastHandler(final JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(UMethod node) {
                visitMethod(context, node);
            }
        };
    }

    @Override
    public List<String> getApplicableAnnotations() {
        return Collections.emptyList();
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return false;
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        // No specific XML checks needed for this detector.
    }
}
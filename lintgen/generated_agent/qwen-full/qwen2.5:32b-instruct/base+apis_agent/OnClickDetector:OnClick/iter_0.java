package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.AnnotationInfo;
import com.android.tools.lint.detector.api.AnnotationUsageInfo;
import com.android.tools.lint.detector.api.AnnotationUsageType;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Attr;

import java.util.Collections;
import java.util.List;

public class OnClickDetector extends Detector implements XmlScanner, SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "OnClickMethodDoesNotExist",
            "The `onClick` method does not exist in the context of this View.",
            "The `onClick` attribute value should be the name of a method in this View's context to invoke when the view is clicked. This name must correspond to a public method that takes exactly one parameter of type `View`.",
            Category.CORRECTNESS,
            5, // Priority
            Severity.ERROR,
            new Implementation(
                    OnClickDetector.class,
                    Scope.JAVA_FILE_SCOPE));

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceFolderType.LAYOUT.equals(folderType);
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("android:onClick");
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String onClickMethod = attribute.getValue();
        if (onClickMethod != null && !onClickMethod.isEmpty()) {
            JavaContext javaContext = context.getJavaContext();
            PsiElement element = javaContext.findElementByName(onClickMethod);
            if (element instanceof PsiMethod) {
                PsiMethod method = (PsiMethod) element;
                if (!method.hasModifierProperty("public") || method.getParameterList().getParametersCount() != 1
                        || !method.getParameterTypes()[0].equals(javaContext.getUastUtils().resolveClass("android.view.View"))) {
                    context.report(ISSUE, attribute,
                            Location.create(attribute),
                            "The `onClick` method does not exist or is not public with a single View parameter.");
                }
            } else {
                context.report(ISSUE, attribute,
                        Location.create(attribute),
                        "The `onClick` method does not exist in the context of this View.");
            }
        }
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("setOnClickListener");
    }

    @Override
    public void visitMethodCall(JavaContext context, UCallExpression node, PsiMethod method) {
        if (method.getName().equals("setOnClickListener")) {
            UReferenceExpression reference = node.getReceiver();
            if (reference != null) {
                PsiElement resolved = reference.resolve();
                if (resolved instanceof UClass && ((UClass) resolved).getQualifiedName().equals("android.view.View.OnClickListener")) {
                    context.report(ISSUE, node,
                            Location.create(node),
                            "The `onClick` method does not exist in the context of this View.");
                }
            }
        }
    }

}
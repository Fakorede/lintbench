package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
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
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.w3c.dom.Attr;

import java.util.Collections;
import java.util.List;

public class OnClickDetector extends Detector implements XmlScanner {

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
    public List<String> getApplicableAttributes() {
        return Collections.singletonList("android:onClick");
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String onClickMethod = attribute.getValue();
        if (onClickMethod != null && !onClickMethod.isEmpty()) {
            JavaContext javaContext = context.getJavaContext();
            UClass containingClass = javaContext.getUastFile().getContainingUClass();

            for (UMethod method : containingClass.getMethods()) {
                if (method.getName().equals(onClickMethod)) {
                    PsiMethod psiMethod = method.getJavaPsi();
                    if (!psiMethod.hasModifierProperty("public") || psiMethod.getParameterList().getParametersCount() != 1
                            || !psiMethod.getParameterTypes()[0].equals(javaContext.getUastResolver().resolveClass("android.view.View"))) {
                        context.report(ISSUE, attribute,
                                Location.create(attribute),
                                "The `onClick` method does not exist or is not public with a single View parameter.");
                    }
                    return;
                }
            }

            context.report(ISSUE, attribute,
                    Location.create(attribute),
                    "The `onClick` method does not exist in the context of this View.");
        }
    }
}
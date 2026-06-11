package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Attr;

import java.util.Collections;
import java.util.List;

public class OnClickDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "OnClickMethodDoesNotExist",
            "The `onClick` method does not exist in the context of this View.",
            "The value of the `onClick` attribute should be a valid method name that exists in the view's context. This method must take exactly one parameter of type `View`.",
            Category.CORRECTNESS,
            5, Severity.ERROR,
            new Implementation(
                    OnClickDetector.class,
                    true
            )
    );

    @Override
    public List<String> getApplicableAttributes() {
        return Collections.singletonList("android:onClick");
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String methodName = attribute.getValue();
        if (methodName != null && !methodName.isEmpty()) {
            UClass containingClass = context.getScope().getEnclosingClass();
            PsiMethod method = findOnClickMethod(containingClass, methodName);
            if (method == null || !isValidOnClickMethod(method)) {
                context.report(ISSUE, attribute, context.getLocation(attribute),
                        "The `onClick` method does not exist or is invalid in the view's context.");
            }
        }
    }

    private PsiMethod findOnClickMethod(UClass containingClass, String methodName) {
        return containingClass.findMethodsByName(methodName).stream()
                .filter(PsiMethod.class::isInstance)
                .findFirst()
                .orElse(null);
    }

    private boolean isValidOnClickMethod(PsiMethod method) {
        if (method == null || !method.hasModifierProperty("public")) {
            return false;
        }
        PsiMethod parameterized = method.getParameterList().getParametersCount() != 1 ? null : method.getParameterTypes()[0].equals(java.lang.Object.class) ? null : method;
        return parameterized != null && parameterized.getParameterList().getParameter(0).getType().equals(java.lang.View.class);
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceFolderType.LAYOUT.equals(folderType);
    }
}
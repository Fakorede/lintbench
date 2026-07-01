package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Attr;

import java.util.Collection;
import java.util.Collections;

public class OnClickDetector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "OnClick",
            "onClick method does not exist",
            "The `onClick` attribute value should be the name of a method in this View's context " +
            "to invoke when the view is clicked. This name must correspond to a public method " +
            "that takes exactly one parameter of type `View`.\n\n" +
            "Must be a string value, using '\\\\;' to escape characters such as '\\\\n' or " +
            "'\\\\uxxxx' for a unicode character.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(OnClickDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_ON_CLICK = "onClick";
    private static final String VIEW_CLASS = "android.view.View";

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_ON_CLICK);
    }

    @Override
    public boolean appliesTo(@NotNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public void visitAttribute(@NotNull XmlContext context, @NotNull Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String methodName = attribute.getValue();
        if (methodName == null || methodName.isEmpty()) {
            return;
        }

        boolean found = checkMethodInProject(context, methodName);
        if (!found) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "Corresponding method handler 'public void " + methodName + "(android.view.View)' not found"
            );
        }
    }

    private boolean checkMethodInProject(@NotNull XmlContext context, @NotNull String methodName) {
        // Iterate through all Java/Kotlin files in the project to find a matching handler.
        // This is the standard approach for layout-to-code resolution in Lint when the exact
        // inflating Activity/Fragment cannot be statically determined.
        for (com.android.tools.lint.detector.api.JavaContext javaContext : context.getDriver().getJavaContexts()) {
            if (hasValidOnClickMethod(javaContext, methodName)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasValidOnClickMethod(@NotNull com.android.tools.lint.detector.api.JavaContext javaContext, @NotNull String methodName) {
        PsiClass psiClass = javaContext.getUastFile() != null ? javaContext.getUastFile().getPsi() : null;
        if (psiClass == null) {
            return false;
        }

        // Check the class itself and its superclasses for the method
        PsiClass current = psiClass;
        while (current != null) {
            for (PsiMethod method : current.getMethods()) {
                if (isValidOnClickMethod(method, methodName)) {
                    return true;
                }
            }
            current = current.getSuperClass();
        }
        return false;
    }

    private boolean isValidOnClickMethod(@NotNull PsiMethod method, @NotNull String expectedName) {
        if (!expectedName.equals(method.getName())) {
            return false;
        }

        if (!method.hasModifierProperty(com.intellij.psi.PsiModifier.PUBLIC)) {
            return false;
        }

        if (!PsiType.VOID.equals(method.getReturnType())) {
            return false;
        }

        PsiParameterList parameterList = method.getParameterList();
        if (parameterList.getParametersCount() != 1) {
            return false;
        }

        PsiParameter param = parameterList.getParameters()[0];
        PsiType paramType = param.getType();
        return VIEW_CLASS.equals(paramType.getCanonicalText());
    }
}
package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
import org.w3c.dom.Attr;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OnClickDetector extends Detector implements Detector.XmlScanner, Detector.UastScanner {
    private final Map<Project, Map<String, Location>> mOnclickMethods = new HashMap<>();

    public static final Issue ISSUE = Issue.create(
            "OnClick",
            "`onClick` method does not exist",
            "The `onClick` attribute value should be the name of a method in this View's context " +
            "to invoke when the view is clicked. This name must correspond to a public method " +
            "that takes exactly one parameter of type `View`.\n\n" +
            "Must be a string value, using '\\\\;' to escape characters such as '\\\\n' or " +
            "'\\\\uxxxx' for a unicode character.",
            Category.CORRECTNESS, 6, Severity.ERROR,
            new Implementation(OnClickDetector.class, Scope.JAVA_AND_RESOURCE_FILES));

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("onClick");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String methodName = attribute.getValue().trim();
        if (methodName.isEmpty()) {
            return;
        }

        Project project = context.getProject();
        mOnclickMethods.computeIfAbsent(project, p -> new HashMap<>())
                .putIfAbsent(methodName, context.getLocation(attribute));
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UMethod.class);
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UMethod method) {
        String name = method.getName();
        Project project = context.getProject();
        Map<String, Location> methods = mOnclickMethods.get(project);
        if (methods == null || !methods.containsKey(name)) {
            return;
        }

        JavaEvaluator evaluator = context.getEvaluator();
        PsiType returnType = method.getReturnType();
        List<UParameter> parameters = method.getUastParameters();

        if (method.hasModifierProperty(PsiModifier.PUBLIC) &&
                PsiType.VOID.equals(returnType) &&
                parameters.size() == 1) {
            UParameter param = parameters.get(0);
            if (evaluator.extendsClass(param.getType(), "android.view.View", false)) {
                methods.remove(name);
            }
        }
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        Map<String, Location> missing = mOnclickMethods.remove(context.getProject());
        if (missing != null) {
            for (Map.Entry<String, Location> entry : missing.entrySet()) {
                context.report(ISSUE, entry.getValue(),
                        "Corresponding method handler '`public void " + entry.getKey() + "(android.view.View)`' not found");
            }
        }
    }
}
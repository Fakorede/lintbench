package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.*;
import com.intellij.psi.PsiType;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
import org.w3c.dom.Attr;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class OnClickDetector extends Detector implements XmlScanner, SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "OnClick",
            "`onClick` method does not exist",
            "The `onClick` attribute value should be the name of a method in this View's context " +
            "to invoke when the view is clicked. This name must correspond to a public method " +
            "that takes exactly one parameter of type `View`.\n\n" +
            "Must be a string value, using '\\;' to escape characters such as '\\n' or '\\uxxxx' for a unicode character.",
            Category.CORRECTNESS, 6, Severity.ERROR,
            new Implementation(OnClickDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE))
    );

    private final Set<String> validMethods = ConcurrentHashMap.newKeySet();

    @Override
    public void beforeCheckProject(Context context) {
        validMethods.clear();
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UMethod.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(UMethod method) {
                JavaEvaluator evaluator = context.getEvaluator();
                if (!evaluator.isPublic(method)) {
                    return;
                }
                PsiType returnType = method.getReturnType();
                if (returnType == null || !returnType.equalsToText("void")) {
                    return;
                }
                List<UParameter> parameters = method.getUastParameters();
                if (parameters.size() != 1) {
                    return;
                }
                if (evaluator.typeMatches(parameters.get(0).getType(), "android.view.View")) {
                    validMethods.add(method.getName());
                }
            }
        };
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_ON_CLICK);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String methodName = attribute.getValue();
        if (methodName == null || methodName.isEmpty()) {
            return;
        }
        if (!validMethods.contains(methodName)) {
            context.report(ISSUE, attribute, context.getLocation(attribute),
                    "Corresponding method handler '" + methodName + "' not found");
        }
    }
}
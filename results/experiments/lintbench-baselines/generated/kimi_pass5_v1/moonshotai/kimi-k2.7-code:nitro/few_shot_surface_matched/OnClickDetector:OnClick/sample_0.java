package com.android.tools.lint.checks;

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
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Attr;

public class OnClickDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ACTIVITY_CLASS = "android.app.Activity";
    private static final String VIEW_CLASS = "android.view.View";
    private static final String ON_CLICK = "onClick";

    public static final Issue ISSUE =
            Issue.create(
                    "OnClick",
                    "OnClick method does not exist",
                    "The `onClick` attribute value should be the name of a method in this View's"
                            + " context to invoke when the view is clicked. This name must"
                            + " correspond to a public method that takes exactly one parameter of"
                            + " type `View`.",
                    Category.CORRECTNESS,
                    8,
                    Severity.ERROR,
                    new Implementation(
                            OnClickDetector.class,
                            EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE)));

    private final List<Handler> mHandlers = new ArrayList<>();

    private static class Handler {
        final String name;
        final XmlContext context;
        final Attr attribute;
        boolean found;

        Handler(String name, XmlContext context, Attr attribute) {
            this.name = name;
            this.context = context;
            this.attribute = attribute;
        }
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ON_CLICK);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }
        mHandlers.add(new Handler(value, context, attribute));
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(ACTIVITY_CLASS);
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        if (declaration.getQualifiedName() == null) {
            return;
        }
        for (Handler handler : mHandlers) {
            if (handler.found) {
                continue;
            }
            for (PsiMethod method : declaration.getJavaPsi().findMethodsByName(handler.name, true)) {
                if (isValidOnClick(method)) {
                    handler.found = true;
                    break;
                }
            }
        }
    }

    @Override
    public void afterCheckRootProject(Context context) {
        for (Handler handler : mHandlers) {
            if (!handler.found) {
                handler.context.report(
                        ISSUE,
                        handler.attribute,
                        handler.context.getLocation(handler.attribute),
                        "Method '" + handler.name + "' is not found");
            }
        }
    }

    private static boolean isValidOnClick(PsiMethod method) {
        if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
            return false;
        }
        PsiParameterList parameterList = method.getParameterList();
        if (parameterList.getParametersCount() != 1) {
            return false;
        }
        PsiParameter parameter = parameterList.getParameters()[0];
        PsiType type = parameter.getType();
        return type.equalsToText(VIEW_CLASS) || VIEW_CLASS.equals(type.getCanonicalText());
    }
}
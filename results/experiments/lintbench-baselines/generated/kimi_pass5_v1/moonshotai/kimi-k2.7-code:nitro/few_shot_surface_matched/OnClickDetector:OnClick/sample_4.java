package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Attr;

public class OnClickDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    private static final String ATTR_ON_CLICK = "onClick";
    private static final String CLASS_ACTIVITY = "android.app.Activity";
    private static final String CLASS_FRAGMENT = "android.app.Fragment";
    private static final String CLASS_SUPPORT_FRAGMENT = "android.support.v4.app.Fragment";
    private static final String CLASS_ANDROIDX_FRAGMENT = "androidx.fragment.app.Fragment";
    private static final String CLASS_VIEW = "android.view.View";

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    OnClickDetector.class, Scope.RESOURCE_FILE_SCOPE, Scope.JAVA_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                            "OnClick",
                            "Corresponding method handler not found",
                            "The `onClick` attribute value should be the name of a method in this "
                                    + "View's context to invoke when the view is clicked. This name "
                                    + "must correspond to a public method that takes exactly one "
                                    + "parameter of type `View`.",
                            Category.CORRECTNESS,
                            10,
                            Severity.ERROR,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

    private Map<String, List<OnClickReference>> mPending;

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_ON_CLICK);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value == null) {
            return;
        }
        value = value.trim();
        if (value.isEmpty() || value.startsWith("@")) {
            return;
        }

        if (mPending == null) {
            mPending = new HashMap<>();
        }
        List<OnClickReference> list = mPending.get(value);
        if (list == null) {
            list = new ArrayList<>();
            mPending.put(value, list);
        }
        list.add(new OnClickReference(context, attribute));
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                CLASS_ACTIVITY,
                CLASS_FRAGMENT,
                CLASS_SUPPORT_FRAGMENT,
                CLASS_ANDROIDX_FRAGMENT);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (mPending == null || mPending.isEmpty()) {
            return;
        }

        for (PsiMethod method : declaration.getMethods()) {
            List<OnClickReference> pending = mPending.get(method.getName());
            if (pending == null || pending.isEmpty()) {
                continue;
            }
            if (isValidOnClickMethod(method)) {
                mPending.remove(method.getName());
            }
        }
    }

    private static boolean isValidOnClickMethod(PsiMethod method) {
        if (!method.hasModifierProperty(PsiModifier.PUBLIC)
                || method.hasModifierProperty(PsiModifier.STATIC)) {
            return false;
        }
        PsiType returnType = method.getReturnType();
        if (returnType == null || !returnType.equalsToText("void")) {
            return false;
        }
        PsiParameterList parameterList = method.getParameterList();
        if (parameterList.getParametersCount() != 1) {
            return false;
        }
        PsiParameter parameter = parameterList.getParameters()[0];
        PsiType type = parameter.getType();
        return type.equalsToText(CLASS_VIEW);
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mPending == null || mPending.isEmpty()) {
            return;
        }

        for (Map.Entry<String, List<OnClickReference>> entry : mPending.entrySet()) {
            String methodName = entry.getKey();
            for (OnClickReference reference : entry.getValue()) {
                reference.context.report(
                        ISSUE,
                        reference.attribute,
                        reference.context.getValueLocation(reference.attribute),
                        "Corresponding method handler '" + methodName + "' not found");
            }
        }
        mPending = null;
    }

    private static class OnClickReference {
        final XmlContext context;
        final Attr attribute;

        OnClickReference(XmlContext context, Attr attribute) {
            this.context = context;
            this.attribute = attribute;
        }
    }
}
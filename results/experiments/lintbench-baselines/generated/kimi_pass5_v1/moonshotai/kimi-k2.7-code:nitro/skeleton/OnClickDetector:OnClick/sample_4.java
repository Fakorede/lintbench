package com.android.tools.lint.checks;

import com.android.tools.lint.detector.LayoutDetector;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.w3c.dom.Attr;

public class OnClickDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    OnClickDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "OnClick",
                    "`onClick` method does not exist",
                    "The `android:onClick` attribute value must name a public method in the "
                            + "view's context that takes a single `android.view.View` parameter.",
                    Category.CORRECTNESS,
                    10,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private final Map<String, List<Location>> mPending = new HashMap<>();
    private final Set<String> mOnClickMethods = new HashSet<>();

    @Override
    public void afterCheckRootProject(Context context) {
        for (Map.Entry<String, List<Location>> entry : mPending.entrySet()) {
            String name = entry.getKey();
            if (!mOnClickMethods.contains(name)) {
                for (Location location : entry.getValue()) {
                    context.report(
                            ISSUE,
                            location,
                            "The corresponding `onClick` method `" + name + "` could not be found");
                }
            }
        }

        mPending.clear();
        mOnClickMethods.clear();
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("onClick");
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }

        List<Location> locations = mPending.get(value);
        if (locations == null) {
            locations = new ArrayList<>();
            mPending.put(value, locations);
        }
        locations.add(context.getValueLocation(attribute));
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.app.Activity");
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        for (UMethod method : declaration.getMethods()) {
            PsiMethod psiMethod = method.getJavaPsi();
            if (psiMethod == null || !psiMethod.hasModifierProperty(PsiModifier.PUBLIC)) {
                continue;
            }
            if (psiMethod.getParameterList().getParametersCount() != 1) {
                continue;
            }
            PsiParameter parameter = psiMethod.getParameterList().getParameter(0);
            if (parameter != null
                    && "android.view.View".equals(parameter.getType().getCanonicalText())) {
                mOnClickMethods.add(psiMethod.getName());
            }
        }
    }
}
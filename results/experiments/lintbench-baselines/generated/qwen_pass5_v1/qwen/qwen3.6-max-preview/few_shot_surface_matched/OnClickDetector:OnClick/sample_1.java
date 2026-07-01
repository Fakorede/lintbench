package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Attr;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.android.SdkConstants.ATTR_ON_CLICK;

public class OnClickDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "OnClick",
            "onClick method does not exist",
            "The `onClick` attribute value should be the name of a method in this View's context " +
            "to invoke when the view is clicked. This name must correspond to a public method " +
            "that takes exactly one parameter of type `View`.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(OnClickDetector.class, Scope.ALL)
    );

    private final Map<String, Location> mRequiredMethods = new HashMap<>();
    private final Set<String> mFoundMethods = new HashSet<>();

    @NonNull
    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_ON_CLICK);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value != null && !value.isEmpty() && !value.startsWith("@") && !value.startsWith("@{")) {
            mRequiredMethods.put(value, context.getLocation(attribute));
        }
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return null;
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        for (PsiMethod method : declaration.getMethods()) {
            if (method.hasModifierProperty("public") && !method.hasModifierProperty("static")) {
                PsiParameter[] parameters = method.getParameterList().getParameters();
                if (parameters.length == 1) {
                    PsiType type = parameters[0].getType();
                    String canonicalText = type.getCanonicalText();
                    if ("android.view.View".equals(canonicalText)) {
                        mFoundMethods.add(method.getName());
                    }
                }
            }
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, Location> entry : mRequiredMethods.entrySet()) {
            if (!mFoundMethods.contains(entry.getKey())) {
                context.report(ISSUE, entry.getValue(),
                        "Corresponding method handler '`public void " + entry.getKey() + "(android.view.View)`' not found");
            }
        }
        mRequiredMethods.clear();
        mFoundMethods.clear();
    }
}
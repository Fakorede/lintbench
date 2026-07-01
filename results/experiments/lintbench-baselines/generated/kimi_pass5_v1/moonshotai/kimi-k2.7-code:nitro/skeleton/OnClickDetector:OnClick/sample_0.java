package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiClass;
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
import org.w3c.dom.Attr;

public class OnClickDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    OnClickDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "OnClick",
                    "`onClick` method does not exist",
                    "The `onClick` attribute value should be the name of a method in this View's "
                            + "context to invoke when the view is clicked. This name must "
                            + "correspond to a public method that takes exactly one parameter "
                            + "of type `View`.\n\n"
                            + "Must be a string value, using '\\;' to escape characters such as "
                            + "'\\n' or '\\uxxxx' for a unicode character.",
                    Category.CORRECTNESS,
                    10,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private final Set<String> mMethods = new HashSet<>();
    private final Map<String, List<Location>> mReferences = new HashMap<>();

    @Override
    public void afterCheckRootProject(Context context) {
        for (Map.Entry<String, List<Location>> entry : mReferences.entrySet()) {
            String name = entry.getKey();
            if (!mMethods.contains(name)) {
                for (Location location : entry.getValue()) {
                    String message =
                            "The `onClick` value `"
                                    + name
                                    + "` does not correspond to a public method in the View's "
                                    + "context that takes an `android.view.View` parameter.";
                    context.report(ISSUE, location, message);
                }
            }
        }
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

        if (!"onClick".equals(attribute.getLocalName())) {
            return;
        }

        List<Location> locations = mReferences.get(value);
        if (locations == null) {
            locations = new ArrayList<>();
            mReferences.put(value, locations);
        }
        locations.add(context.getValueLocation(attribute));
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.content.Context");
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        PsiClass psiClass = declaration.getJavaPsi();
        if (psiClass == null) {
            return;
        }

        for (PsiMethod method : psiClass.getAllMethods()) {
            if (method.hasModifierProperty(PsiModifier.PUBLIC)
                    && method.getParameterList().getParametersCount() == 1) {
                PsiParameter parameter = method.getParameterList().getParameters()[0];
                if ("android.view.View".equals(parameter.getType().getCanonicalText())) {
                    mMethods.add(method.getName());
                }
            }
        }
    }
}
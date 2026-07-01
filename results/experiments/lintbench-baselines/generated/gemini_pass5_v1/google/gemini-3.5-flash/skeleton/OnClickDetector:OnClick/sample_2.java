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
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
import org.w3c.dom.Attr;

public class OnClickDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(OnClickDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "OnClick",
                    "`onClick` method does not exist",
                    "The `onClick` attribute value should be the name of a method in this View's context to invoke when the view is clicked. This name must correspond to a public method that takes exactly one parameter of type `View`.",
                    Category.CORRECTNESS,
                    10,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private final List<OnClickRef> mReferences = new ArrayList<>();
    private final Set<String> mValidMethods = new HashSet<>();

    private static class OnClickRef {
        final String methodName;
        final Location location;

        OnClickRef(String methodName, Location location) {
            this.methodName = methodName;
            this.location = location;
        }
    }

    @Override
    public void afterCheckRootProject(Context context) {
        if (!context.getScope().contains(Scope.JAVA_FILE)) {
            return;
        }

        for (OnClickRef ref : mReferences) {
            if (!mValidMethods.contains(ref.methodName)) {
                context.report(
                        ISSUE,
                        ref.location,
                        String.format("Corresponding method %s(View) does not exist or is not public", ref.methodName));
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
        if (value.isEmpty() || value.startsWith("@") || value.contains("{")) {
            // Skip empty, data binding, or resource-bound onClicks
            return;
        }
        mReferences.add(new OnClickRef(value, context.getLocation(attribute)));
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.app.Activity");
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        for (UMethod method : declaration.getMethods()) {
            if (method.hasModifierProperty(PsiModifier.PUBLIC)) {
                List<UParameter> parameters = method.getUastParameters();
                if (parameters.size() == 1) {
                    PsiType type = parameters.get(0).getType();
                    if (type.getCanonicalText().equals("android.view.View")) {
                        mValidMethods.add(method.getName());
                    }
                }
            }
        }
    }
}
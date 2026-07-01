package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Attr;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class OnClickDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    OnClickDetector.class,
                    Scope.RESOURCE_AND_JAVA_RANGE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "OnClick",
                    "onClick method does not exist",
                    "The `onClick` attribute value should be the name of a method in this View's "
                            + "context to invoke when the view is clicked. This name must correspond "
                            + "to a public method that takes exactly one parameter of type `View`.",
                    Category.CORRECTNESS,
                    10,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private final List<OnClickDeclaration> xmlDeclarations = new ArrayList<>();
    private final Set<String> definedMethods = new HashSet<>();
    private boolean hasJavaFiles = false;

    private static class OnClickDeclaration {
        final String name;
        final Location location;

        OnClickDeclaration(String name, Location location) {
            this.name = name;
            this.location = location;
        }
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("onClick");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!"http://schemas.android.com/apk/res/android".equals(attribute.getNamespaceURI())) {
            return;
        }
        String value = attribute.getValue();
        if (value.isEmpty() || value.startsWith("@") || value.startsWith("{")) {
            return;
        }
        xmlDeclarations.add(new OnClickDeclaration(value, context.getLocation(attribute)));
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.app.Activity");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        hasJavaFiles = true;
        for (PsiMethod method : declaration.getAllMethods()) {
            if (method.hasModifierProperty(PsiModifier.PUBLIC)) {
                PsiParameter[] parameters = method.getParameterList().getParameters();
                if (parameters.length == 1) {
                    PsiType type = parameters[0].getType();
                    if ("android.view.View".equals(type.getCanonicalText())) {
                        definedMethods.add(method.getName());
                    }
                }
            }
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (!hasJavaFiles) {
            return;
        }
        for (OnClickDeclaration declaration : xmlDeclarations) {
            if (!definedMethods.contains(declaration.name)) {
                context.report(
                        ISSUE,
                        declaration.location,
                        "Corresponding method `public void " + declaration.name + "(View)` does not exist");
            }
        }
    }
}
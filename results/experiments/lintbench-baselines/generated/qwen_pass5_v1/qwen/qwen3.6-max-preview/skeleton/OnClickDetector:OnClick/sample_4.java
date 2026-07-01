package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiModifier;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
import org.w3c.dom.Attr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class OnClickDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(OnClickDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "OnClick",
                    "`onClick` method does not exist",
                    "The `onClick` attribute value should be the name of a method in this View's context " +
                    "to invoke when the view is clicked. This name must correspond to a public method " +
                    "that takes exactly one parameter of type `View`.",
                    Category.CORRECTNESS,
                    10,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private final List<XmlReference> xmlReferences = new ArrayList<>();
    private final Set<String> validMethods = new HashSet<>();

    private static class XmlReference {
        final XmlContext context;
        final Attr attribute;
        final String methodName;

        XmlReference(XmlContext context, Attr attribute, String methodName) {
            this.context = context;
            this.attribute = attribute;
            this.methodName = methodName;
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (XmlReference ref : xmlReferences) {
            if (!validMethods.contains(ref.methodName)) {
                String message = "Corresponding method handler '`public void " + ref.methodName + "(android.view.View)`' not found";
                ref.context.report(ISSUE, ref.attribute, ref.context.getLocation(ref.attribute), message);
            }
        }
        xmlReferences.clear();
        validMethods.clear();
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("onClick");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String methodName = attribute.getValue();
        if (methodName != null && !methodName.isEmpty()) {
            xmlReferences.add(new XmlReference(context, attribute, methodName));
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList("android.app.Activity", "android.view.View", "androidx.fragment.app.Fragment");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        for (UMethod method : declaration.getMethods()) {
            if (method.hasModifierProperty(PsiModifier.PUBLIC) &&
                !method.hasModifierProperty(PsiModifier.STATIC) &&
                method.getUastParameters().size() == 1) {
                UParameter param = method.getUastParameters().get(0);
                if (param.getType() != null && "android.view.View".equals(param.getType().getCanonicalText())) {
                    validMethods.add(method.getName());
                }
            }
        }
    }
}
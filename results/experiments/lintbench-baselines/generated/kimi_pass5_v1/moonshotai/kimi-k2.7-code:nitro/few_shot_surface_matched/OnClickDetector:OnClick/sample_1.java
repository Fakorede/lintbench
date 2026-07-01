package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_ON_CLICK;
import static com.android.SdkConstants.VIEW_CLASS;

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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Attr;

public class OnClickDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "OnClick",
                    "OnClick method does not exist",
                    "The `onClick` attribute value should be the name of a method in this View's "
                            + "context to invoke when the view is clicked. This name must "
                            + "correspond to a public method that takes exactly one parameter "
                            + "of type `View`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            OnClickDetector.class,
                            Scope.JAVA_FILE_SCOPE,
                            Scope.RESOURCE_FILE_SCOPE));

    private final List<OnClickReference> references = new ArrayList<>();
    private final Set<String> validHandlers = new HashSet<>();

    public OnClickDetector() {}

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_ON_CLICK);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }
        references.add(new OnClickReference(value, context.getLocation(attribute)));
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return null;
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        for (PsiMethod method : declaration.getMethods()) {
            if (isValidOnClickHandler(method)) {
                validHandlers.add(method.getName());
            }
        }
    }

    private static boolean isValidOnClickHandler(@NonNull PsiMethod method) {
        if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
            return false;
        }
        PsiType returnType = method.getReturnType();
        if (returnType == null || !returnType.equalsToText("void")) {
            return false;
        }
        PsiParameter[] parameters = method.getParameterList().getParameters();
        if (parameters.length != 1) {
            return false;
        }
        return parameters[0].getType().equalsToText(VIEW_CLASS);
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (OnClickReference reference : references) {
            if (!validHandlers.contains(reference.name)) {
                context.report(
                        ISSUE,
                        reference.location,
                        "Corresponding method handler '"
                                + reference.name
                                + "' not found for onClick attribute");
            }
        }
    }

    private static class OnClickReference {
        final String name;
        final Location location;

        OnClickReference(@NonNull String name, @NonNull Location location) {
            this.name = name;
            this.location = location;
        }
    }
}
package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
import org.w3c.dom.Attr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OnClickDetector extends LayoutDetector implements Detector.UastScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(OnClickDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "OnClick",
                    "`onClick` method does not exist",
                    "The `onClick` attribute value should be the name of a method in this View's context to invoke when the view is clicked. This name must correspond to a public method that takes exactly one parameter of type `View`.\n\nMust be a string value, using '\\;' to escape characters such as '\\n' or '\\uxxxx' for a unicode character.",
                    Category.CORRECTNESS,
                    10,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private final Set<String> validMethods = new HashSet<>();
    private final Map<String, List<Location>> xmlUsages = new HashMap<>();

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (!xmlUsages.isEmpty()) {
            for (Map.Entry<String, List<Location>> entry : xmlUsages.entrySet()) {
                if (!validMethods.contains(entry.getKey())) {
                    String message = "Corresponding method handler '" + entry.getKey() + "(android.view.View)' not found";
                    for (Location location : entry.getValue()) {
                        context.report(ISSUE, location, message);
                    }
                }
            }
        }
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("onClick");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String methodName = attribute.getValue();
        if (methodName != null && !methodName.isEmpty()) {
            xmlUsages.computeIfAbsent(methodName, k -> new ArrayList<>()).add(context.getLocation(attribute));
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.app.Activity",
                "android.view.View",
                "androidx.fragment.app.Fragment",
                "android.support.v4.app.Fragment"
        );
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        for (UMethod method : declaration.getMethods()) {
            if (method.hasModifier(PsiModifier.PUBLIC)) {
                PsiType returnType = method.getReturnType();
                if (returnType != null && PsiType.VOID.equals(returnType)) {
                    List<UParameter> parameters = method.getUastParameters();
                    if (parameters.size() == 1) {
                        UParameter param = parameters.get(0);
                        PsiType paramType = param.getType();
                        if (paramType != null && "android.view.View".equals(paramType.getCanonicalText())) {
                            validMethods.add(method.getName());
                        }
                    }
                }
            }
        }
    }
}
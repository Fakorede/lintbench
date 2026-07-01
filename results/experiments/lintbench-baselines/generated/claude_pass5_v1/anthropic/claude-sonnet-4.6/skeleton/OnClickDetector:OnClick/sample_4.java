package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Attr;

public class OnClickDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(OnClickDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "OnClick",
                    "`onClick` method does not exist",
                    "The `onClick` attribute value should be the name of a method in this View's "
                            + "context to invoke when the view is clicked. This name must correspond "
                            + "to a public method that takes exactly one parameter of type `View`.\n"
                            + "\n"
                            + "Must be a string value, using '\\;' to escape characters such as "
                            + "'\\n' or '\\uxxxx' for a unicode character.",
                    Category.CORRECTNESS,
                    10,
                    Severity.ERROR,
                    IMPLEMENTATION);

    /**
     * Map from onClick method name to the locations in XML where they are referenced.
     * These are methods we need to find in Java source.
     */
    private final Map<String, List<Location>> mNames = new HashMap<>();

    /**
     * Set of method names that have been found in Java source.
     */
    private final List<String> mFound = new ArrayList<>();

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mNames.isEmpty()) {
            return;
        }

        // Report any onClick methods that were referenced in XML but not found in Java
        for (Map.Entry<String, List<Location>> entry : mNames.entrySet()) {
            String name = entry.getKey();
            if (!mFound.contains(name)) {
                List<Location> locations = entry.getValue();
                for (Location location : locations) {
                    context.report(
                            ISSUE,
                            location,
                            String.format(
                                    "onClick method `%1$s` does not exist in the associated activity; "
                                            + "did you forget to add it? Or is it private?",
                                    name));
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
        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getValueLocation(attribute),
                    "onClick attribute value cannot be empty");
            return;
        }

        // Record the reference to this onClick method name along with its location
        Location location = context.getValueLocation(attribute);
        List<Location> locations = mNames.get(value);
        if (locations == null) {
            locations = new ArrayList<>();
            mNames.put(value, locations);
        }
        locations.add(location);
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.app.Activity",
                "android.app.Fragment",
                "androidx.fragment.app.Fragment",
                "android.support.v4.app.Fragment"
        );
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (mNames.isEmpty()) {
            return;
        }

        // Look for methods in this class that match the onClick method names
        PsiClass psiClass = declaration.getJavaPsi();
        checkClass(context, psiClass);
    }

    private void checkClass(@NonNull JavaContext context, @NonNull PsiClass psiClass) {
        for (String name : new ArrayList<>(mNames.keySet())) {
            if (mFound.contains(name)) {
                continue;
            }

            // Search for the method in this class and its superclasses
            PsiMethod[] methods = psiClass.findMethodsByName(name, true);
            for (PsiMethod method : methods) {
                if (isValidOnClickMethod(method)) {
                    mFound.add(name);
                    break;
                }
            }
        }
    }

    /**
     * Returns true if the given method is a valid onClick handler:
     * - public
     * - returns void
     * - takes exactly one parameter of type View
     */
    private static boolean isValidOnClickMethod(@NonNull PsiMethod method) {
        // Must be public
        if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
            return false;
        }

        // Must return void
        PsiType returnType = method.getReturnType();
        if (returnType == null || !returnType.equals(PsiType.VOID)) {
            return false;
        }

        // Must take exactly one parameter of type View
        PsiParameterList parameterList = method.getParameterList();
        PsiParameter[] parameters = parameterList.getParameters();
        if (parameters.length != 1) {
            return false;
        }

        PsiType paramType = parameters[0].getType();
        String canonicalText = paramType.getCanonicalText();
        return "android.view.View".equals(canonicalText)
                || canonicalText.endsWith(".View");
    }
}
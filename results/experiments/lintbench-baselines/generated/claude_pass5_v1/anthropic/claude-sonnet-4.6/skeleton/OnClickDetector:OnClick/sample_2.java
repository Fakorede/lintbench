package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
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
                            + "context to invoke when the view is clicked. This name must correspond to a "
                            + "public method that takes exactly one parameter of type `View`.\n"
                            + "\n"
                            + "Must be a string value, using '\\;' to escape characters such as '\\n' or "
                            + "'\\uxxxx' for a unicode character.",
                    Category.CORRECTNESS,
                    10,
                    Severity.ERROR,
                    IMPLEMENTATION);

    /**
     * Map from onClick method name to the list of locations in XML where the method is referenced.
     * These are methods we need to find in Java code.
     */
    private Map<String, List<Location>> mNames;

    /**
     * Set of onClick method names that have been found in Java code.
     */
    private Set<String> mFound;

    /**
     * Map from onClick method name to a list of locations where the method exists but with
     * wrong signature (not public, wrong parameters, etc.).
     */
    private Map<String, List<String>> mWrongSignature;

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

        // Trim whitespace
        String methodName = value.trim();
        if (methodName.isEmpty()) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getValueLocation(attribute),
                    "onClick attribute value cannot be empty");
            return;
        }

        if (mNames == null) {
            mNames = new HashMap<>();
        }

        List<Location> locations = mNames.get(methodName);
        if (locations == null) {
            locations = new ArrayList<>();
            mNames.put(methodName, locations);
        }
        locations.add(context.getValueLocation(attribute));
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.app.Activity",
                "android.app.Fragment",
                "androidx.fragment.app.Fragment",
                "android.support.v4.app.Fragment");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (mNames == null || mNames.isEmpty()) {
            return;
        }

        if (mFound == null) {
            mFound = new HashSet<>();
        }
        if (mWrongSignature == null) {
            mWrongSignature = new HashMap<>();
        }

        PsiClass psiClass = declaration.getJavaPsi();
        if (psiClass == null) {
            return;
        }

        for (String methodName : mNames.keySet()) {
            PsiMethod[] methods = psiClass.findMethodsByName(methodName, true);
            for (PsiMethod method : methods) {
                // Check if the method has the correct signature:
                // public void methodName(View view)
                boolean isPublic = method.getModifierList().hasModifierProperty(PsiModifier.PUBLIC);
                PsiParameter[] parameters = method.getParameterList().getParameters();
                boolean hasCorrectParams = false;
                if (parameters.length == 1) {
                    PsiType paramType = parameters[0].getType();
                    String canonicalText = paramType.getCanonicalText();
                    if ("android.view.View".equals(canonicalText)
                            || "View".equals(canonicalText)) {
                        hasCorrectParams = true;
                    }
                }

                if (isPublic && hasCorrectParams) {
                    mFound.add(methodName);
                    break;
                } else {
                    // Wrong signature - record it
                    List<String> issues = mWrongSignature.get(methodName);
                    if (issues == null) {
                        issues = new ArrayList<>();
                        mWrongSignature.put(methodName, issues);
                    }
                    if (!isPublic) {
                        issues.add("must be public");
                    }
                    if (!hasCorrectParams) {
                        issues.add("must have a single View parameter");
                    }
                }
            }
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mNames == null || mNames.isEmpty()) {
            return;
        }

        if (mFound == null) {
            mFound = new HashSet<>();
        }
        if (mWrongSignature == null) {
            mWrongSignature = new HashMap<>();
        }

        for (Map.Entry<String, List<Location>> entry : mNames.entrySet()) {
            String name = entry.getKey();
            List<Location> locations = entry.getValue();

            if (mFound.contains(name)) {
                // Method was found with correct signature, no issue
                continue;
            }

            for (Location location : locations) {
                List<String> problems = mWrongSignature.get(name);
                String message;
                if (problems != null && !problems.isEmpty()) {
                    // Method exists but has wrong signature
                    StringBuilder sb = new StringBuilder();
                    sb.append("onClick handler `").append(name).append("` has wrong signature: ");
                    sb.append(String.join(", ", problems));
                    message = sb.toString();
                } else {
                    message =
                            String.format(
                                    "onClick handler `%1$s` is not public",
                                    name);
                    // Check if we have any wrong signature info
                    message =
                            String.format(
                                    "Corresponding method handler '`public void %1$s(android.view.View)`' not found",
                                    name);
                }
                context.report(ISSUE, location, message);
            }
        }

        // Reset state
        mNames = null;
        mFound = null;
        mWrongSignature = null;
    }
}
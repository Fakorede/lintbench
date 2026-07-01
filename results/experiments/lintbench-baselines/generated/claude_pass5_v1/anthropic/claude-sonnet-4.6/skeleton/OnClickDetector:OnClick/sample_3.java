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
     * Map from onClick method name to list of locations where that method name is referenced
     * in XML layout files.
     */
    private Map<String, List<Location>> mNames;

    /**
     * Set of onClick method names that have been found in Java/Kotlin source files
     * with the correct signature (public, single View parameter).
     */
    private Set<String> mFound;

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mNames = new HashMap<>();
        mFound = new HashSet<>();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mNames != null && !mNames.isEmpty() && mFound != null) {
            // Report any onClick method names that were referenced in XML but not found
            // in any Activity/Fragment/View class
            for (Map.Entry<String, List<Location>> entry : mNames.entrySet()) {
                String name = entry.getKey();
                if (!mFound.contains(name)) {
                    List<Location> locations = entry.getValue();
                    for (Location location : locations) {
                        context.report(
                                ISSUE,
                                location,
                                String.format(
                                        "Corresponding method handler `public void %s(android.view.View)` not found",
                                        name));
                    }
                }
            }
        }
        mNames = null;
        mFound = null;
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

        // Check for spaces in the method name
        if (value.indexOf(' ') != -1) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getValueLocation(attribute),
                    "There should be no spaces in the `onClick` handler name");
            return;
        }

        // Record this reference for later cross-checking against Java files
        if (mNames == null) {
            mNames = new HashMap<>();
        }
        List<Location> locations = mNames.get(value);
        if (locations == null) {
            locations = new ArrayList<>();
            mNames.put(value, locations);
        }
        locations.add(context.getValueLocation(attribute));
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.app.Activity",
                "android.app.Fragment",
                "androidx.fragment.app.Fragment",
                "android.support.v4.app.Fragment",
                "android.view.View");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (mNames == null || mNames.isEmpty()) {
            return;
        }

        // Look through the methods of this class to find matching onClick handlers
        for (PsiMethod method : declaration.getMethods()) {
            String methodName = method.getName();
            if (!mNames.containsKey(methodName)) {
                continue;
            }

            // Check that the method is public
            if (!method.getModifierList().hasModifierProperty(PsiModifier.PUBLIC)) {
                continue;
            }

            // Check that the method has exactly one parameter of type View
            PsiParameterList parameterList = method.getParameterList();
            if (parameterList.getParametersCount() != 1) {
                continue;
            }

            PsiParameter parameter = parameterList.getParameters()[0];
            PsiType type = parameter.getType();
            String canonicalText = type.getCanonicalText();

            if ("android.view.View".equals(canonicalText)) {
                if (mFound == null) {
                    mFound = new HashSet<>();
                }
                mFound.add(methodName);
            }
        }
    }
}
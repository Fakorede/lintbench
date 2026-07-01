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
                            + "context to invoke when the view is clicked. This name must correspond to a "
                            + "public method that takes exactly one parameter of type `View`.\n"
                            + "\n"
                            + "Must be a string value, using '\\;' to escape characters such as '\\n' or "
                            + "'\\uxxxx' for a unicode character.",
                    Category.CORRECTNESS,
                    10,
                    Severity.ERROR,
                    IMPLEMENTATION);

    /** Map from method name to locations in XML where that onClick method is referenced */
    private Map<String, List<Location>> mNames;

    /** Set of method names that have been found in Java source */
    private Set<String> mFoundMethods;

    /** Whether we've checked the Java files yet */
    private boolean mHaveJavaResults;

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mNames = new HashMap<>();
        mFoundMethods = new HashSet<>();
        mHaveJavaResults = false;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mNames != null && !mNames.isEmpty()) {
            for (Map.Entry<String, List<Location>> entry : mNames.entrySet()) {
                String name = entry.getKey();
                if (!mFoundMethods.contains(name)) {
                    List<Location> locations = entry.getValue();
                    for (Location location : locations) {
                        String message = String.format(
                                "onClick attribute value `%1$s` is not a public `void` method "
                                        + "that takes a `android.view.View` argument in the activity",
                                name);
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
        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "onClick attribute value cannot be empty");
            return;
        }

        // Check for invalid characters (spaces, etc.)
        if (value.indexOf(' ') != -1) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    String.format(
                            "There should be no spaces in the `onClick` handler name `%1$s`",
                            value));
            return;
        }

        if (mNames == null) {
            mNames = new HashMap<>();
        }

        List<Location> locations = mNames.get(value);
        if (locations == null) {
            locations = new ArrayList<>();
            mNames.put(value, locations);
        }
        locations.add(context.getLocation(attribute));
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

        // Look through all methods in this class (and its superclasses) for onClick candidates
        PsiClass psiClass = declaration.getJavaPsi();
        if (psiClass == null) {
            return;
        }

        checkClass(psiClass);
    }

    private void checkClass(@NonNull PsiClass psiClass) {
        if (mNames == null) {
            return;
        }

        for (String name : mNames.keySet()) {
            if (mFoundMethods.contains(name)) {
                continue;
            }

            // Check if this class has a matching method
            PsiMethod[] methods = psiClass.findMethodsByName(name, true);
            for (PsiMethod method : methods) {
                if (isValidOnClickMethod(method)) {
                    mFoundMethods.add(name);
                    break;
                }
            }
        }
    }

    private static boolean isValidOnClickMethod(@NonNull PsiMethod method) {
        // Must be public
        if (!method.getModifierList().hasModifierProperty(PsiModifier.PUBLIC)) {
            return false;
        }

        // Must return void
        PsiType returnType = method.getReturnType();
        if (returnType == null || !returnType.equals(PsiType.VOID)) {
            return false;
        }

        // Must take exactly one parameter of type View
        PsiParameterList parameterList = method.getParameterList();
        if (parameterList.getParametersCount() != 1) {
            return false;
        }

        PsiParameter parameter = parameterList.getParameters()[0];
        PsiType paramType = parameter.getType();
        String canonicalText = paramType.getCanonicalText();

        return canonicalText.equals("android.view.View")
                || canonicalText.equals("View");
    }
}
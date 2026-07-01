package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
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
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Attr;

public class OnClickDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "OnClick",
                    "onClick method does not exist",
                    "The `onClick` attribute value should be the name of a method in this View's"
                            + " context to invoke when the view is clicked. This name must"
                            + " correspond to a public method that takes exactly one parameter of"
                            + " type `View`.\n\nMust be a string value, using '\\\\;' to escape"
                            + " characters such as '\\\\n' or '\\\\uxxxx' for a unicode character.",
                    Category.CORRECTNESS,
                    10,
                    Severity.ERROR,
                    new Implementation(
                            OnClickDetector.class,
                            EnumSet.of(Scope.ALL_RESOURCE_FILES, Scope.ALL_JAVA_FILES)));

    private static final String ATTR_ON_CLICK = "onClick";
    private static final String ANDROID_VIEW = "android.view.View";

    /** Map from method name to list of locations in XML where that onClick is referenced */
    private final Map<String, List<Location.Handle>> methodToLocations = new HashMap<>();

    /** Set of method names that have been found in Activity/Context subclasses */
    private final Map<String, Boolean> validMethods = new HashMap<>();

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_ON_CLICK);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String methodName = attribute.getValue();
        if (methodName == null || methodName.isEmpty()) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "onClick attribute value cannot be empty");
            return;
        }

        methodName = methodName.trim();

        List<Location.Handle> handles =
                methodToLocations.computeIfAbsent(methodName, k -> new ArrayList<>());
        handles.add(context.createLocationHandle(attribute));
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.app.Activity");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        for (PsiMethod method : declaration.getAllMethods()) {
            String name = method.getName();
            if (!methodToLocations.containsKey(name)) {
                continue;
            }
            if (isValidOnClickMethod(method)) {
                validMethods.put(name, Boolean.TRUE);
            }
        }
    }

    private boolean isValidOnClickMethod(@NonNull PsiMethod method) {
        // Must be public
        if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
            return false;
        }

        // Must not be static
        if (method.hasModifierProperty(PsiModifier.STATIC)) {
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
        if (!ANDROID_VIEW.equals(canonicalText)) {
            return false;
        }

        return true;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, List<Location.Handle>> entry : methodToLocations.entrySet()) {
            String methodName = entry.getKey();
            if (!validMethods.containsKey(methodName)) {
                List<Location.Handle> handles = entry.getValue();
                for (Location.Handle handle : handles) {
                    Location location = handle.resolve();
                    context.report(
                            ISSUE,
                            location,
                            String.format(
                                    "onClick method `%1$s` does not exist in the enclosing"
                                            + " context or is not public with a single `View`"
                                            + " parameter",
                                    methodName));
                }
            }
        }
    }
}
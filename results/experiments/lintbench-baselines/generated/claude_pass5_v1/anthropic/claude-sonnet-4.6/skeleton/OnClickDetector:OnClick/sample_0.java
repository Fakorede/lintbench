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

    /** Map from onClick method name to list of locations where it's referenced in XML */
    private Map<String, List<Location>> mNames;

    /** Set of method names that have been found in Java/Kotlin source */
    private Set<String> mMethods;

    /** Whether we've found any Activity classes */
    private boolean mHaveJavaFile;

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mNames = new HashMap<>();
        mMethods = new HashSet<>();
        mHaveJavaFile = false;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mNames != null && !mNames.isEmpty() && mHaveJavaFile) {
            for (Map.Entry<String, List<Location>> entry : mNames.entrySet()) {
                String name = entry.getKey();
                if (!mMethods.contains(name)) {
                    List<Location> locations = entry.getValue();
                    for (Location location : locations) {
                        String message = String.format(
                                "onClick handler `%1$s` is not public",
                                name);
                        // We report a more specific message based on what we know
                        String generalMessage = String.format(
                                "onClick handler `%1$s` does not exist",
                                name);
                        context.report(ISSUE, location, generalMessage);
                    }
                }
            }
        }

        mNames = null;
        mMethods = null;
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
                    "onClick attribute is empty");
            return;
        }

        // Check for spaces or invalid characters
        if (value.indexOf(' ') != -1 || value.indexOf('\t') != -1) {
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
        mHaveJavaFile = true;

        if (mNames == null || mNames.isEmpty()) {
            return;
        }

        // Look through the methods of this class for onClick handlers
        for (PsiMethod method : declaration.getMethods()) {
            String methodName = method.getName();
            if (!mNames.containsKey(methodName)) {
                continue;
            }

            // Check that the method is public
            if (!method.getModifierList().hasModifierProperty(PsiModifier.PUBLIC)) {
                List<Location> locations = mNames.get(methodName);
                if (locations != null) {
                    Location methodLocation = context.getLocation(method);
                    for (Location location : locations) {
                        String message = String.format(
                                "onClick handler `%1$s` must be public",
                                methodName);
                        context.report(ISSUE, location, message);
                    }
                    mNames.remove(methodName);
                }
                continue;
            }

            // Check that the method takes exactly one parameter of type View
            PsiParameterList parameterList = method.getParameterList();
            PsiParameter[] parameters = parameterList.getParameters();
            if (parameters.length != 1) {
                List<Location> locations = mNames.get(methodName);
                if (locations != null) {
                    for (Location location : locations) {
                        String message = String.format(
                                "onClick handler `%1$s` must take exactly one parameter (a `View`)",
                                methodName);
                        context.report(ISSUE, location, message);
                    }
                    mNames.remove(methodName);
                }
                continue;
            }

            PsiType paramType = parameters[0].getType();
            String canonicalText = paramType.getCanonicalText();
            if (!"android.view.View".equals(canonicalText) && !"View".equals(canonicalText)) {
                List<Location> locations = mNames.get(methodName);
                if (locations != null) {
                    for (Location location : locations) {
                        String message = String.format(
                                "onClick handler `%1$s` should have signature "
                                        + "`void %1$s(android.view.View)`",
                                methodName);
                        context.report(ISSUE, location, message);
                    }
                    mNames.remove(methodName);
                }
                continue;
            }

            // Valid method found - add to known methods and remove from pending
            if (mMethods == null) {
                mMethods = new HashSet<>();
            }
            mMethods.add(methodName);
            mNames.remove(methodName);
        }
    }
}
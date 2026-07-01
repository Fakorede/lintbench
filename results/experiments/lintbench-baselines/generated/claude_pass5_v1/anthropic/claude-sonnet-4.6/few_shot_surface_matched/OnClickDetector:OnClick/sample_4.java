package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_ON_CLICK;

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
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import java.util.ArrayList;
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

public class OnClickDetector extends LayoutDetector implements SourceCodeScanner {

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

    private static final String ANDROID_VIEW = "android.view.View";
    private static final String ACTIVITY_CLASS = "android.app.Activity";
    private static final String FRAGMENT_CLASS = "android.app.Fragment";
    private static final String SUPPORT_FRAGMENT_CLASS = "androidx.fragment.app.Fragment";
    private static final String SUPPORT_FRAGMENT_CLASS_OLD = "android.support.v4.app.Fragment";

    /** Map from method name to locations where it is referenced from onClick attributes */
    private final Map<String, List<Location.Handle>> mNames = new HashMap<>();

    /** Set of method names that have been found in visited classes */
    private final Set<String> mFoundMethods = new HashSet<>();

    /** Map from method name to a description of why it's wrong (wrong signature, not public, etc.) */
    private final Map<String, String> mWrongMethods = new HashMap<>();

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_ON_CLICK);
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

        // Store the reference for later checking
        List<Location.Handle> handles = mNames.get(value);
        if (handles == null) {
            handles = new ArrayList<>();
            mNames.put(value, handles);
        }
        handles.add(context.createLocationHandle(attribute));
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        List<String> superClasses = new ArrayList<>();
        superClasses.add(ACTIVITY_CLASS);
        superClasses.add(FRAGMENT_CLASS);
        superClasses.add(SUPPORT_FRAGMENT_CLASS);
        superClasses.add(SUPPORT_FRAGMENT_CLASS_OLD);
        return superClasses;
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        for (PsiMethod method : declaration.getAllMethods()) {
            String name = method.getName();
            if (!mNames.containsKey(name)) {
                continue;
            }

            // Check if the method has the right signature
            if (!context.getEvaluator().isPublic(method)) {
                if (!mFoundMethods.contains(name)) {
                    mWrongMethods.put(name, "must be public");
                }
                continue;
            }

            if (method.isConstructor()) {
                continue;
            }

            PsiParameter[] parameters = method.getParameterList().getParameters();
            if (parameters.length != 1) {
                if (!mFoundMethods.contains(name)) {
                    mWrongMethods.put(name,
                            String.format(
                                    "should have signature `void %s(android.view.View)`", name));
                }
                continue;
            }

            PsiType paramType = parameters[0].getType();
            if (!paramType.getCanonicalText().equals(ANDROID_VIEW)) {
                if (!mFoundMethods.contains(name)) {
                    mWrongMethods.put(name,
                            String.format(
                                    "should have signature `void %s(android.view.View)`", name));
                }
                continue;
            }

            // Method found with correct signature
            mFoundMethods.add(name);
            mWrongMethods.remove(name);
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mNames.isEmpty()) {
            return;
        }

        for (Map.Entry<String, List<Location.Handle>> entry : mNames.entrySet()) {
            String name = entry.getKey();
            List<Location.Handle> handles = entry.getValue();

            if (mFoundMethods.contains(name)) {
                continue;
            }

            String message;
            if (mWrongMethods.containsKey(name)) {
                message = String.format(
                        "onClick handler `%1$s` has wrong signature (%2$s)",
                        name, mWrongMethods.get(name));
            } else {
                message = String.format(
                        "onClick handler `%1$s` not found in any Activity, Fragment or "
                                + "enclosing class (must be a public method that takes a "
                                + "`android.view.View` parameter)",
                        name);
            }

            for (Location.Handle handle : handles) {
                Location location = handle.resolve();
                context.report(ISSUE, location, message);
            }
        }
    }
}
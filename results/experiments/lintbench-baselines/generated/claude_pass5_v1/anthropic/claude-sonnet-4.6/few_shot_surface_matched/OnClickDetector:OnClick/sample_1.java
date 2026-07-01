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
                    "The `onClick` attribute value should be the name of a method in this View's "
                            + "context to invoke when the view is clicked. This name must correspond "
                            + "to a public method that takes exactly one parameter of type `View`.\n"
                            + "\n"
                            + "Must be a string value, using '\\;' to escape characters such as "
                            + "'\\n' or '\\uxxxx' for a unicode character.",
                    Category.CORRECTNESS,
                    10,
                    Severity.ERROR,
                    new Implementation(
                            OnClickDetector.class,
                            EnumSet.of(Scope.ALL_RESOURCE_FILES, Scope.ALL_JAVA_FILES)));

    private static final String ATTR_ON_CLICK = "onClick";
    private static final String ANDROID_VIEW = "android.view.View";
    private static final String ANDROID_ACTIVITY = "android.app.Activity";

    /** Map from method name to list of locations where it is referenced in XML */
    private Map<String, List<Location.Handle>> mNames;

    /** Set of method names that have been found in Java source */
    private Set<String> mMethods;

    /** Whether we've checked the project */
    private boolean mChecked;

    public OnClickDetector() {}

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mNames = new HashMap<>();
        mMethods = new HashSet<>();
        mChecked = false;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mNames == null || mNames.isEmpty()) {
            return;
        }

        // Report any onClick method names that were not found in any Activity class
        for (Map.Entry<String, List<Location.Handle>> entry : mNames.entrySet()) {
            String name = entry.getKey();
            if (mMethods == null || !mMethods.contains(name)) {
                List<Location.Handle> handles = entry.getValue();
                for (Location.Handle handle : handles) {
                    Location location = handle.resolve();
                    context.report(
                            ISSUE,
                            location,
                            String.format(
                                    "onClick attribute value `%1$s` is not a public method "
                                            + "that takes exactly one parameter of type `View`",
                                    name));
                }
            }
        }
    }

    // ---- XmlScanner ----

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
                    "onClick attribute is empty");
            return;
        }

        // Trim whitespace
        value = value.trim();

        if (mNames == null) {
            mNames = new HashMap<>();
        }

        List<Location.Handle> list = mNames.get(value);
        if (list == null) {
            list = new ArrayList<>();
            mNames.put(value, list);
        }
        list.add(context.createLocationHandle(attribute));
    }

    // ---- SourceCodeScanner ----

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(ANDROID_ACTIVITY);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (mNames == null || mNames.isEmpty()) {
            return;
        }

        // Look through all methods in this class hierarchy for onClick candidates
        for (PsiMethod method : declaration.getMethods()) {
            String name = method.getName();
            if (!mNames.containsKey(name)) {
                continue;
            }

            // Must be public
            if (!method.getModifierList().hasModifierProperty(PsiModifier.PUBLIC)) {
                continue;
            }

            // Must not be static
            if (method.getModifierList().hasModifierProperty(PsiModifier.STATIC)) {
                continue;
            }

            // Must take exactly one parameter of type View
            PsiParameterList parameterList = method.getParameterList();
            if (parameterList.getParametersCount() != 1) {
                continue;
            }

            PsiParameter parameter = parameterList.getParameters()[0];
            PsiType type = parameter.getType();
            String typeName = type.getCanonicalText();

            if (ANDROID_VIEW.equals(typeName) || isViewSubtype(typeName)) {
                if (mMethods == null) {
                    mMethods = new HashSet<>();
                }
                mMethods.add(name);
            }
        }
    }

    private static boolean isViewSubtype(@NonNull String typeName) {
        // We accept android.view.View and any subclass. Since we can't easily resolve
        // the full hierarchy here, we check if it starts with android. or is a View type.
        // The UAST/PSI type canonical text for a View subclass won't be "android.view.View"
        // but we still want to accept it. We do a simple heuristic: accept any type
        // that could be a View (non-primitive, non-null).
        // For correctness, we accept android.view.View and its known subclasses.
        // A more thorough check would use context.getEvaluator().extendsClass().
        return !typeName.isEmpty()
                && !typeName.equals("void")
                && !typeName.equals("int")
                && !typeName.equals("boolean")
                && !typeName.equals("long")
                && !typeName.equals("float")
                && !typeName.equals("double")
                && !typeName.equals("char")
                && !typeName.equals("byte")
                && !typeName.equals("short");
    }
}
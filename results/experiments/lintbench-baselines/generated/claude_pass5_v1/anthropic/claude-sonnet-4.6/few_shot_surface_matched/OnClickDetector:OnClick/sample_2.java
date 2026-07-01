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
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;

import org.jetbrains.uast.UClass;
import org.w3c.dom.Attr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private static final String ACTIVITY_CLASS = "android.app.Activity";
    private static final String CONTEXT_CLASS = "android.content.Context";

    /** Map from method name to list of locations in XML where it's referenced */
    private final Map<String, List<Location.Handle>> mNames = new HashMap<>();

    /** Set of method names that have been found in Java source */
    private final Map<String, Boolean> mMethods = new HashMap<>();

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

        // Check for whitespace or invalid characters
        if (!value.equals(value.trim())) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    String.format(
                            "There should be no whitespace around the method name `%1$s`", value));
            return;
        }

        List<Location.Handle> list = mNames.get(value);
        if (list == null) {
            list = new ArrayList<>();
            mNames.put(value, list);
        }
        list.add(context.createLocationHandle(attribute));
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(ACTIVITY_CLASS);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        for (PsiMethod method : declaration.getAllMethods()) {
            String name = method.getName();
            if (!mNames.containsKey(name)) {
                continue;
            }

            // Check if method is public
            if (!method.getModifierList().hasModifierProperty("public")) {
                continue;
            }

            // Check that method takes exactly one parameter of type View
            PsiParameter[] parameters = method.getParameterList().getParameters();
            if (parameters.length != 1) {
                continue;
            }

            PsiType paramType = parameters[0].getType();
            String canonicalText = paramType.getCanonicalText();
            if (ANDROID_VIEW.equals(canonicalText)) {
                mMethods.put(name, Boolean.TRUE);
            }
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mNames.isEmpty()) {
            return;
        }

        for (Map.Entry<String, List<Location.Handle>> entry : mNames.entrySet()) {
            String name = entry.getKey();
            if (!mMethods.containsKey(name)) {
                List<Location.Handle> handles = entry.getValue();
                for (Location.Handle handle : handles) {
                    Location location = handle.resolve();
                    context.report(
                            ISSUE,
                            location,
                            String.format(
                                    "Corresponding method handler `public void %1$s(android.view.View)` not found",
                                    name));
                }
            }
        }
    }
}
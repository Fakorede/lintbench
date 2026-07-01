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
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
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

    private static final String ANDROID_VIEW = "android.view.View";
    private static final String ANDROID_APP_ACTIVITY = "android.app.Activity";

    /** Map from method name to list of locations in XML where that onClick is referenced */
    private Map<String, List<Location.Handle>> mNames;

    /** Set of method names that have been found in Java source */
    private Map<String, Boolean> mJavaMethods;

    public OnClickDetector() {}

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mNames = new HashMap<>();
        mJavaMethods = new HashMap<>();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mNames != null && !mNames.isEmpty()) {
            for (Map.Entry<String, List<Location.Handle>> entry : mNames.entrySet()) {
                String name = entry.getKey();
                List<Location.Handle> handles = entry.getValue();
                Boolean valid = mJavaMethods.get(name);
                if (valid == null) {
                    // Method not found at all
                    for (Location.Handle handle : handles) {
                        Location location = handle.resolve();
                        context.report(
                                ISSUE,
                                location,
                                String.format(
                                        "Corresponding method handler '`public void %s(android.view.View)`' not found",
                                        name));
                    }
                } else if (!valid) {
                    // Method found but with wrong signature
                    for (Location.Handle handle : handles) {
                        Location location = handle.resolve();
                        context.report(
                                ISSUE,
                                location,
                                String.format(
                                        "Method '`%s`' must be public and take a single `View` parameter",
                                        name));
                    }
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
                    "onClick attribute value must not be empty");
            return;
        }

        // Trim whitespace
        value = value.trim();

        if (mNames == null) {
            mNames = new HashMap<>();
        }

        List<Location.Handle> handles = mNames.get(value);
        if (handles == null) {
            handles = new ArrayList<>();
            mNames.put(value, handles);
        }
        handles.add(context.createLocationHandle(attribute));
    }

    // ---- SourceCodeScanner ----

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(ANDROID_APP_ACTIVITY);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (mNames == null || mNames.isEmpty()) {
            return;
        }

        for (PsiMethod method : declaration.getAllMethods()) {
            String name = method.getName();
            if (!mNames.containsKey(name)) {
                continue;
            }

            boolean isPublic = method.getModifierList().hasModifierProperty(PsiModifier.PUBLIC);
            boolean isStatic = method.getModifierList().hasModifierProperty(PsiModifier.STATIC);

            if (!isPublic || isStatic) {
                // Only mark as invalid if not already found as valid
                if (!Boolean.TRUE.equals(mJavaMethods.get(name))) {
                    mJavaMethods.put(name, false);
                }
                continue;
            }

            PsiParameterList paramList = method.getParameterList();
            PsiParameter[] params = paramList.getParameters();
            if (params.length == 1) {
                PsiType type = params[0].getType();
                String canonicalText = type.getCanonicalText();
                if (ANDROID_VIEW.equals(canonicalText)) {
                    // Valid method found
                    mJavaMethods.put(name, true);
                    continue;
                }
            }

            // Wrong signature
            if (!Boolean.TRUE.equals(mJavaMethods.get(name))) {
                mJavaMethods.put(name, false);
            }
        }
    }
}
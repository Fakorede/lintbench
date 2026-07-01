package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;

import org.jetbrains.uast.UClass;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OnClickDetector extends Detector implements XmlScanner, SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "OnClick",
            "`onClick` method does not exist",
            "The `onClick` attribute value should be the name of a method in this View's " +
            "context to invoke when the view is clicked. This name must correspond to a " +
            "public method that takes exactly one parameter of type `View`.\n" +
            "\n" +
            "Must be a string value, using '\\;' to escape characters such as '\\n' or " +
            "'\\uxxxx' for a unicode character.",
            Category.CORRECTNESS,
            10,
            Severity.ERROR,
            new Implementation(
                    OnClickDetector.class,
                    EnumSet.of(Scope.ALL_RESOURCE_FILES, Scope.ALL_JAVA_FILES)
            )
    );

    private static final String ATTR_ON_CLICK = "onClick";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    /** Map from method name to list of locations in XML where it's referenced */
    private final Map<String, List<Location>> mNames = new HashMap<>();

    /** Map from method name to whether it was found in Java/Kotlin source */
    private final Map<String, Boolean> mFound = new HashMap<>();

    /** Map from method name to whether the found method has wrong signature */
    private final Map<String, String> mWrongSignature = new HashMap<>();

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mNames.clear();
        mFound.clear();
        mWrongSignature.clear();
    }

    // ---- XmlScanner ----

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_ON_CLICK);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String methodName = attribute.getValue();
        if (methodName == null || methodName.isEmpty()) {
            context.report(ISSUE, attribute, context.getLocation(attribute),
                    "onClick attribute value cannot be empty");
            return;
        }

        // Trim whitespace
        methodName = methodName.trim();

        if (!mNames.containsKey(methodName)) {
            mNames.put(methodName, new ArrayList<>());
        }
        mNames.get(methodName).add(context.getLocation(attribute));
    }

    // ---- SourceCodeScanner ----

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.app.Activity");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        checkClass(context, declaration);
    }

    private void checkClass(@NonNull JavaContext context, @NonNull UClass uClass) {
        PsiClass psiClass = uClass.getJavaPsi();
        for (String methodName : mNames.keySet()) {
            if (mFound.containsKey(methodName) && mFound.get(methodName)) {
                continue;
            }
            // Look for the method in this class
            PsiMethod[] methods = psiClass.findMethodsByName(methodName, true);
            for (PsiMethod method : methods) {
                if (isValidOnClickMethod(method)) {
                    mFound.put(methodName, true);
                    mWrongSignature.remove(methodName);
                    break;
                } else {
                    // Found a method with the right name but wrong signature
                    if (!mFound.containsKey(methodName) || !mFound.get(methodName)) {
                        mWrongSignature.put(methodName, describeWrongSignature(method));
                    }
                }
            }
        }
    }

    private boolean isValidOnClickMethod(@NonNull PsiMethod method) {
        // Must be public
        if (!method.getModifierList().hasModifierProperty(PsiModifier.PUBLIC)) {
            return false;
        }
        // Must not be static
        if (method.getModifierList().hasModifierProperty(PsiModifier.STATIC)) {
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
        return canonicalText.equals("android.view.View") || canonicalText.equals("View");
    }

    private String describeWrongSignature(@NonNull PsiMethod method) {
        StringBuilder sb = new StringBuilder();
        PsiParameterList parameterList = method.getParameterList();
        boolean isPublic = method.getModifierList().hasModifierProperty(PsiModifier.PUBLIC);
        boolean isStatic = method.getModifierList().hasModifierProperty(PsiModifier.STATIC);
        PsiType returnType = method.getReturnType();

        if (!isPublic) {
            sb.append("must be public");
        }
        if (isStatic) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("must not be static");
        }
        if (returnType != null && !returnType.equals(PsiType.VOID)) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("must return void");
        }
        if (parameterList.getParametersCount() != 1) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("must have exactly one parameter of type View");
        } else {
            PsiParameter parameter = parameterList.getParameters()[0];
            PsiType paramType = parameter.getType();
            String canonicalText = paramType.getCanonicalText();
            if (!canonicalText.equals("android.view.View") && !canonicalText.equals("View")) {
                if (sb.length() > 0) sb.append(", ");
                sb.append("parameter must be of type View (not ").append(canonicalText).append(")");
            }
        }

        return sb.length() > 0 ? sb.toString() : "wrong signature";
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Report any onClick references that were not found in source
        for (Map.Entry<String, List<Location>> entry : mNames.entrySet()) {
            String methodName = entry.getKey();
            List<Location> locations = entry.getValue();

            Boolean found = mFound.get(methodName);
            if (found != null && found) {
                continue;
            }

            String wrongSig = mWrongSignature.get(methodName);
            String message;
            if (wrongSig != null) {
                message = String.format(
                        "Method `%1$s` referenced in the layout file must be public, " +
                        "non-static, return void, and take exactly one parameter of type View (%2$s)",
                        methodName, wrongSig);
            } else {
                message = String.format(
                        "Method `%1$s` referenced in the layout file must be " +
                        "declared public `void %1$s(View v)` in the context",
                        methodName);
            }

            for (Location location : locations) {
                context.report(ISSUE, location, message);
            }
        }
    }
}
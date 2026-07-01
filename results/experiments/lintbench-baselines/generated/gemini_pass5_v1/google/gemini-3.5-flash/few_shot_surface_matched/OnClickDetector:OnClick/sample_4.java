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
import org.jetbrains.uast.UClass;
import org.w3c.dom.Attr;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OnClickDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "OnClick",
                    "onClick method does not exist",
                    "The `onClick` attribute value should be the name of a method in this View's context "
                            + "to invoke when the view is clicked. This name must correspond to a public "
                            + "method that takes exactly one parameter of type `View`.",
                    Category.CORRECTNESS,
                    10,
                    Severity.ERROR,
                    new Implementation(
                            OnClickDetector.class,
                            EnumSet.of(Scope.ALL_RESOURCE_FILES, Scope.JAVA_FILE_SCOPE)
                    )
            );

    private final Map<String, List<LocationInfo>> mOnClickMethods = new HashMap<>();
    private final Set<String> mDefinedMethods = new HashSet<>();

    private static class LocationInfo {
        final XmlContext context;
        final Attr attribute;
        final Location location;

        LocationInfo(XmlContext context, Attr attribute, Location location) {
            this.context = context;
            this.attribute = attribute;
            this.location = location;
        }
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("onClick");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value.isEmpty() || value.startsWith("@")) {
            return;
        }
        value = value.trim();
        if (value.isEmpty()) {
            return;
        }
        if (!Character.isJavaIdentifierStart(value.charAt(0))) {
            return;
        }
        for (int i = 1; i < value.length(); i++) {
            if (!Character.isJavaIdentifierPart(value.charAt(i))) {
                return;
            }
        }
        List<LocationInfo> list = mOnClickMethods.computeIfAbsent(value, k -> new ArrayList<>());
        list.add(new LocationInfo(context, attribute, context.getLocation(attribute)));
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.app.Activity");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        for (PsiMethod method : declaration.getMethods()) {
            if (isValidOnClickMethod(method)) {
                mDefinedMethods.add(method.getName());
            }
        }
    }

    private boolean isValidOnClickMethod(PsiMethod method) {
        if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
            return false;
        }
        PsiType returnType = method.getReturnType();
        if (returnType == null || !PsiType.VOID.equals(returnType)) {
            return false;
        }
        PsiParameterList parameterList = method.getParameterList();
        if (parameterList.getParametersCount() != 1) {
            return false;
        }
        PsiParameter parameter = parameterList.getParameters()[0];
        PsiType type = parameter.getType();
        return "android.view.View".equals(type.getCanonicalText());
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (!context.getScope().contains(Scope.JAVA_FILE)) {
            return;
        }
        for (Map.Entry<String, List<LocationInfo>> entry : mOnClickMethods.entrySet()) {
            String methodName = entry.getKey();
            if (!mDefinedMethods.contains(methodName)) {
                for (LocationInfo info : entry.getValue()) {
                    info.context.report(
                            ISSUE,
                            info.attribute,
                            info.location,
                            String.format("Corresponding method handler `public void %s(View)` not found", methodName)
                    );
                }
            }
        }
    }
}
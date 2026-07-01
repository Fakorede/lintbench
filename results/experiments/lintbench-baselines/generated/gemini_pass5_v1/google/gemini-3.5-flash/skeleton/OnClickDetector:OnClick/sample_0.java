package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
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
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Attr;

public class OnClickDetector extends LayoutDetector implements Detector.UastScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(OnClickDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "OnClick",
                    "`onClick` method does not exist",
                    "The `onClick` attribute value should be the name of a method in this View's context to invoke when the view is clicked. This name must correspond to a public method that takes exactly one parameter of type `View`.",
                    Category.CORRECTNESS,
                    10,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private final List<OnClickAttribute> onClickAttributes = new ArrayList<>();
    private final Map<String, List<String>> classToMethods = new HashMap<>();

    private static class OnClickAttribute {
        final String methodName;
        final String toolsContext;
        final Location location;

        OnClickAttribute(String methodName, String toolsContext, Location location) {
            this.methodName = methodName;
            this.toolsContext = toolsContext;
            this.location = location;
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (onClickAttributes.isEmpty()) {
            return;
        }
        if (classToMethods.isEmpty()) {
            return;
        }

        for (OnClickAttribute attribute : onClickAttributes) {
            String methodName = attribute.methodName;
            String toolsContext = attribute.toolsContext;

            boolean found = false;
            if (toolsContext != null && !toolsContext.isEmpty()) {
                for (Map.Entry<String, List<String>> entry : classToMethods.entrySet()) {
                    String fqcn = entry.getKey();
                    if (classMatches(fqcn, toolsContext)) {
                        if (entry.getValue().contains(methodName)) {
                            found = true;
                            break;
                        }
                    }
                }
            } else {
                for (List<String> methods : classToMethods.values()) {
                    if (methods.contains(methodName)) {
                        found = true;
                        break;
                    }
                }
            }

            if (!found) {
                String message;
                if (toolsContext != null && !toolsContext.isEmpty()) {
                    message = String.format("Corresponding method `public void %s(View)` not found in `%s`", methodName, toolsContext);
                } else {
                    message = String.format("Corresponding method `public void %s(View)` not found in any Activity", methodName);
                }
                context.report(ISSUE, attribute.location, message);
            }
        }
    }

    private boolean classMatches(String fqcn, String toolsContext) {
        if (fqcn == null || toolsContext == null) return false;
        if (toolsContext.isEmpty()) return false;

        if (toolsContext.startsWith(".")) {
            return fqcn.endsWith(toolsContext);
        }
        if (!toolsContext.contains(".")) {
            return fqcn.endsWith("." + toolsContext);
        }
        return fqcn.equals(toolsContext);
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("onClick");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String methodName = attribute.getValue();
        if (methodName.isEmpty() || methodName.startsWith("@")) {
            return;
        }

        String toolsContext = null;
        org.w3c.dom.Element root = attribute.getOwnerDocument().getDocumentElement();
        if (root != null) {
            toolsContext = root.getAttributeNS("http://schemas.android.com/tools", "context");
        }

        Location location = context.getLocation(attribute);
        onClickAttributes.add(new OnClickAttribute(methodName, toolsContext, location));
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.app.Activity");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        String fqcn = declaration.getQualifiedName();
        if (fqcn == null) {
            return;
        }

        List<String> methods = new ArrayList<>();
        for (PsiMethod method : declaration.getMethods()) {
            if (method.hasModifierProperty(PsiModifier.PUBLIC)) {
                PsiType returnType = method.getReturnType();
                if (returnType != null && "void".equals(returnType.getCanonicalText())) {
                    PsiParameterList parameterList = method.getParameterList();
                    if (parameterList.getParametersCount() == 1) {
                        PsiParameter parameter = parameterList.getParameters()[0];
                        PsiType paramType = parameter.getType();
                        String typeText = paramType.getCanonicalText();
                        if ("android.view.View".equals(typeText) || "View".equals(typeText)) {
                            methods.add(method.getName());
                        }
                    }
                }
            }
        }
        classToMethods.put(fqcn, methods);
    }
}
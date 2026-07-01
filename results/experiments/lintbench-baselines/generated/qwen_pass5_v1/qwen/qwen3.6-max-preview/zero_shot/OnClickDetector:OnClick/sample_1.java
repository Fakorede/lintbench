package com.android.tools.lint.checks;

import com.android.SdkConstants;
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
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
import org.jetbrains.uast.UElement;
import org.w3c.dom.Attr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OnClickDetector extends Detector implements Detector.XmlScanner, Detector.UastScanner {

    public static final Issue ISSUE = Issue.create(
            "OnClick",
            "`onClick` method does not exist",
            "The `onClick` attribute value should be the name of a method in this View's context " +
            "to invoke when the view is clicked. This name must correspond to a public method " +
            "that takes exactly one parameter of type `View`.\n\n" +
            "Must be a string value, using '\\\\;' to escape characters such as '\\\\n' or " +
            "'\\\\uxxxx' for a unicode character.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(OnClickDetector.class, Scope.JAVA_FILE_SCOPE, Scope.RESOURCE_FILE_SCOPE));

    private Map<String, List<Location>> xmlReferences;
    private Set<String> validMethods;

    @Override
    public void beforeCheckProject(@NotNull Context context) {
        xmlReferences = new HashMap<>();
        validMethods = new HashSet<>();
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_ON_CLICK);
    }

    @Override
    public void visitAttribute(@NotNull XmlContext context, @NotNull Attr attribute) {
        String methodName = attribute.getValue();
        if (methodName != null && !methodName.isEmpty()) {
            xmlReferences.computeIfAbsent(methodName, k -> new ArrayList<>())
                    .add(context.getLocation(attribute));
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UMethod.class);
    }

    @Override
    public void visitMethod(@NotNull JavaContext context, @NotNull UMethod method) {
        if (!method.hasModifierProperty("public")) {
            return;
        }

        List<UParameter> parameters = method.getUastParameters();
        if (parameters.size() != 1) {
            return;
        }

        UParameter param = parameters.get(0);
        PsiType type = param.getType();
        if (type != null && context.getEvaluator().extendsClass(type, SdkConstants.CLASS_VIEW, false)) {
            validMethods.add(method.getName());
        }
    }

    @Override
    public void afterCheckProject(@NotNull Context context) {
        if (xmlReferences == null || validMethods == null) {
            return;
        }

        for (Map.Entry<String, List<Location>> entry : xmlReferences.entrySet()) {
            String methodName = entry.getKey();
            if (!validMethods.contains(methodName)) {
                String message = String.format("Corresponding method handler '%s(View)' not found", methodName);
                for (Location location : entry.getValue()) {
                    context.report(ISSUE, location, message);
                }
            }
        }
    }
}
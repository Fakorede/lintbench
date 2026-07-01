package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.*;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
import org.w3c.dom.Attr;

import java.util.*;

public class OnClickDetector extends Detector implements XmlScanner, SourceCodeScanner {

    private Map<String, List<Location>> mXmlReferences;
    private Set<String> mValidMethods;

    public static final Issue ISSUE = Issue.create(
            "OnClick",
            "`onClick` method does not exist",
            "The `onClick` attribute value should be the name of a method in this View's context " +
            "to invoke when the view is clicked. This name must correspond to a public method " +
            "that takes exactly one parameter of type `View`.\n\n" +
            "Must be a string value, using '\\\\;' to escape characters such as '\\\\n' or '\\\\uxxxx' for a unicode character.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(OnClickDetector.class, Scope.JAVA_AND_RESOURCE_FILES)
    );

    @Override
    public void beforeCheckProject(Context context) {
        mXmlReferences = new HashMap<>();
        mValidMethods = new HashSet<>();
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("onClick");
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }
        String methodName = attribute.getValue();
        if (methodName != null && !methodName.isEmpty()) {
            mXmlReferences.computeIfAbsent(methodName, k -> new ArrayList<>())
                    .add(context.getLocation(attribute));
        }
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(UMethod node) {
                if (!node.hasModifier(PsiModifier.PUBLIC)) {
                    return;
                }
                PsiType returnType = node.getReturnType();
                if (returnType == null || !context.getEvaluator().isVoid(returnType)) {
                    return;
                }
                List<UParameter> parameters = node.getUastParameters();
                if (parameters.size() != 1) {
                    return;
                }
                PsiType paramType = parameters.get(0).getType();
                if (context.getEvaluator().typeMatches(paramType, "android.view.View")) {
                    mValidMethods.add(node.getName());
                }
            }
        };
    }

    @Override
    public void afterCheckProject(Context context) {
        if (mXmlReferences == null || mValidMethods == null) {
            return;
        }
        for (Map.Entry<String, List<Location>> entry : mXmlReferences.entrySet()) {
            String methodName = entry.getKey();
            if (!mValidMethods.contains(methodName)) {
                for (Location location : entry.getValue()) {
                    context.report(ISSUE, location,
                            "Corresponding method handler '`public void " + methodName + "(android.view.View)`' not found");
                }
            }
        }
    }
}
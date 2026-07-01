package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.client.api.UastParser;
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
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class OnClickDetector extends LayoutDetector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "OnClick",
            "`onClick` method does not exist",
            "The `onClick` attribute value should be the name of a method in this View's context "
                    + "to invoke when the view is clicked. This name must correspond to a public method "
                    + "that takes exactly one parameter of type `View`.\n\n"
                    + "Must be a string value, using '\\;' to escape characters such as '\\n' or "
                    + "'\\uxxxx' for a unicode character.",
            Category.CORRECTNESS,
            10,
            Severity.ERROR,
            new Implementation(
                    OnClickDetector.class,
                    Scope.JAVA_AND_RESOURCE_FILES
            )
    );

    private final Set<String> declaredMethods = new HashSet<>();
    private final List<PendingReport> pendingReports = new ArrayList<>();

    @Override
    public void beforeCheckRootProject(Context context) {
        declaredMethods.clear();
        pendingReports.clear();
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_ON_CLICK);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }
        String methodName = attribute.getValue();
        if (methodName.isEmpty() || methodName.startsWith("@")) {
            return;
        }

        Element root = attribute.getOwnerDocument().getDocumentElement();
        String contextClass = null;
        if (root != null) {
            contextClass = root.getAttributeNS(SdkConstants.TOOLS_URI, "context");
        }

        if (contextClass != null && !contextClass.isEmpty()) {
            String fqcn = getFullyQualifiedClassName(context, contextClass);
            if (fqcn != null) {
                UastParser parser = context.getClient().getUastParser(context.getProject());
                JavaEvaluator evaluator = parser.getEvaluator();
                PsiClass psiClass = evaluator.findClass(fqcn);
                if (psiClass != null) {
                    if (!hasOnClickMethod(psiClass, methodName)) {
                        context.report(
                                ISSUE,
                                attribute,
                                context.getValueLocation(attribute),
                                String.format("Method '%s' should be defined in '%s' (public void %s(View))", methodName, fqcn, methodName)
                        );
                    }
                    return;
                }
            }
        }

        pendingReports.add(new PendingReport(context, context.getValueLocation(attribute), methodName, attribute));
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UMethod.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(UMethod method) {
                if (isValidOnClickMethod(method)) {
                    declaredMethods.add(method.getName());
                }
            }
        };
    }

    @Override
    public void afterCheckRootProject(Context context) {
        if (!context.getScope().contains(Scope.JAVA_FILE)) {
            return;
        }
        for (PendingReport report : pendingReports) {
            if (!declaredMethods.contains(report.methodName)) {
                report.context.report(
                        ISSUE,
                        report.attribute,
                        report.location,
                        String.format("Corresponding method 'public void %s(View)' not found", report.methodName)
                );
            }
        }
    }

    private boolean hasOnClickMethod(PsiClass psiClass, String methodName) {
        PsiMethod[] methods = psiClass.findMethodsByName(methodName, true);
        for (PsiMethod method : methods) {
            if (isValidOnClickMethod(method)) {
                return true;
            }
        }
        return false;
    }

    private boolean isValidOnClickMethod(PsiMethod method) {
        if (!method.getModifierList().hasModifierProperty(PsiModifier.PUBLIC)) {
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
        PsiType paramType = parameter.getType();
        return "android.view.View".equals(paramType.getCanonicalText());
    }

    private String getFullyQualifiedClassName(XmlContext context, String relativeClassName) {
        if (relativeClassName == null || relativeClassName.isEmpty()) {
            return null;
        }
        if (relativeClassName.startsWith(".")) {
            String pkg = context.getProject().getPackage();
            if (pkg != null) {
                return pkg + relativeClassName;
            }
        } else if (!relativeClassName.contains(".")) {
            String pkg = context.getProject().getPackage();
            if (pkg != null) {
                return pkg + "." + relativeClassName;
            }
        }
        return relativeClassName;
    }

    private static class PendingReport {
        final XmlContext context;
        final Location location;
        final String methodName;
        final Attr attribute;

        PendingReport(XmlContext context, Location location, String methodName, Attr attribute) {
            this.context = context;
            this.location = location;
            this.methodName = methodName;
            this.attribute = attribute;
        }
    }
}
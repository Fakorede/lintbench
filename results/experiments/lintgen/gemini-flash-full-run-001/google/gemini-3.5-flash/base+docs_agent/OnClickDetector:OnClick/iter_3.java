package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.client.api.UElementHandler;
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
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.client.api.UastParser;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class OnClickDetector extends Detector implements XmlScanner, SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "OnClick",
            "onClick method does not exist",
            "The `onClick` attribute value should be the name of a method in this View's context " +
            "to invoke when the view is clicked. This name must correspond to a public method " +
            "that takes exactly one parameter of type `View`.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(OnClickDetector.class, Scope.JAVA_AND_RESOURCE_FILES)
    );

    private final List<OnClickOccurrence> occurrences = new ArrayList<>();
    private final Set<String> validMethods = new HashSet<>();
    private final Set<String> validMethodNames = new HashSet<>();
    private final Map<String, String> superClasses = new HashMap<>();

    @Override
    public void beforeCheckRootProject(Context context) {
        occurrences.clear();
        validMethods.clear();
        validMethodNames.clear();
        superClasses.clear();
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("onClick");
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String namespace = attribute.getNamespaceURI();
        if (namespace != null && !SdkConstants.ANDROID_URI.equals(namespace)) {
            return;
        }
        String methodName = attribute.getValue();
        if (methodName == null || methodName.isEmpty() || methodName.contains("{") || methodName.contains("@")) {
            return;
        }

        Element element = attribute.getOwnerElement();
        String contextClass = getToolsContext(element);

        Location location = context.getLocation(attribute);
        occurrences.add(new OnClickOccurrence(methodName, location, contextClass, context.getProject().getPackage()));
    }

    private String getToolsContext(Element element) {
        Element current = element;
        while (current != null) {
            String context = current.getAttributeNS(SdkConstants.TOOLS_URI, "context");
            if (context != null && !context.isEmpty()) {
                return context;
            }
            context = current.getAttribute("tools:context");
            if (context != null && !context.isEmpty()) {
                return context;
            }
            current = current.getParentNode() instanceof Element ? (Element) current.getParentNode() : null;
        }
        return null;
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        List<Class<? extends UElement>> types = new ArrayList<>();
        types.add(UMethod.class);
        types.add(UClass.class);
        return types;
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(UClass node) {
                PsiClass current = node;
                while (current != null) {
                    PsiClass superClass = current.getSuperClass();
                    if (superClass != null) {
                        String currentQName = current.getQualifiedName();
                        String superQName = superClass.getQualifiedName();
                        if (currentQName != null && superQName != null) {
                            superClasses.put(currentQName, superQName);
                        }
                        current = superClass;
                    } else {
                        break;
                    }
                }
            }

            @Override
            public void visitMethod(UMethod method) {
                if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
                    return;
                }
                List<UParameter> parameters = method.getUastParameters();
                if (parameters.size() != 1) {
                    return;
                }
                UParameter parameter = parameters.get(0);
                PsiType type = parameter.getType();
                if (context.getEvaluator().typeMatches(type, "android.view.View")) {
                    String methodName = method.getName();
                    PsiClass containingClass = method.getContainingClass();
                    if (containingClass != null) {
                        String qName = containingClass.getQualifiedName();
                        if (qName != null) {
                            validMethods.add(qName + "#" + methodName);
                        }
                        validMethodNames.add(methodName);
                    }
                }
            }
        };
    }

    @Override
    public void afterCheckRootProject(Context context) {
        UastParser parser = context.getClient().getUastParser(context.getProject());
        JavaEvaluator evaluator = parser != null ? parser.getEvaluator() : null;

        for (OnClickOccurrence occurrence : occurrences) {
            String methodName = occurrence.methodName;
            String contextClass = occurrence.contextClass;
            String packageName = occurrence.packageName;

            if (contextClass != null && !contextClass.isEmpty()) {
                String fqcn = contextClass;
                if (fqcn.startsWith(".")) {
                    if (packageName != null) {
                        fqcn = packageName + fqcn;
                    }
                } else if (!fqcn.contains(".")) {
                    if (packageName != null) {
                        fqcn = packageName + "." + fqcn;
                    }
                }

                boolean found = false;
                if (evaluator != null) {
                    PsiClass psiClass = evaluator.findClass(fqcn);
                    if (psiClass == null && fqcn.startsWith(".")) {
                        psiClass = evaluator.findClass(fqcn.substring(1));
                    }
                    if (psiClass != null) {
                        found = hasOnClickMethod(psiClass, methodName, evaluator);
                    }
                }

                if (!found) {
                    String currentClass = fqcn;
                    Set<String> visited = new HashSet<>();
                    while (currentClass != null && visited.add(currentClass)) {
                        if (validMethods.contains(currentClass + "#" + methodName)) {
                            found = true;
                            break;
                        }
                        currentClass = superClasses.get(currentClass);
                    }
                }

                if (!found) {
                    context.report(
                            ISSUE,
                            occurrence.location,
                            "Method '" + methodName + "' not found in context class '" + fqcn + "'"
                    );
                }
            } else {
                if (!validMethodNames.contains(methodName)) {
                    context.report(
                            ISSUE,
                            occurrence.location,
                            "Corresponding method '" + methodName + "(View)' not found"
                    );
                }
            }
        }
    }

    private boolean hasOnClickMethod(PsiClass psiClass, String methodName, JavaEvaluator evaluator) {
        PsiClass current = psiClass;
        while (current != null) {
            for (PsiMethod method : current.findMethodsByName(methodName, false)) {
                if (isValidOnClickMethod(method, evaluator)) {
                    return true;
                }
            }
            current = current.getSuperClass();
        }
        return false;
    }

    private boolean isValidOnClickMethod(PsiMethod method, JavaEvaluator evaluator) {
        if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
            return false;
        }
        PsiParameterList parameterList = method.getParameterList();
        if (parameterList.getParametersCount() != 1) {
            return false;
        }
        PsiParameter parameter = parameterList.getParameters()[0];
        PsiType type = parameter.getType();
        return evaluator.typeMatches(type, "android.view.View");
    }

    private static class OnClickOccurrence {
        final String methodName;
        final Location location;
        final String contextClass;
        final String packageName;

        OnClickOccurrence(String methodName, Location location, String contextClass, String packageName) {
            this.methodName = methodName;
            this.location = location;
            this.contextClass = contextClass;
            this.packageName = packageName;
        }
    }
}
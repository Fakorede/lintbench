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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        if (!SdkConstants.ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }
        String methodName = attribute.getValue();
        if (methodName == null || methodName.isEmpty() || methodName.startsWith("@{")) {
            return;
        }

        Element root = attribute.getOwnerDocument().getDocumentElement();
        String contextClass = null;
        if (root != null) {
            contextClass = root.getAttributeNS("http://schemas.android.com/tools", "context");
        }

        Location location = context.getLocation(attribute);
        occurrences.add(new OnClickOccurrence(methodName, location, contextClass, context.getProject().getPackage()));
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
                if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
                    return;
                }
                List<UParameter> parameters = method.getUastParameters();
                if (parameters.size() != 1) {
                    return;
                }
                UParameter parameter = parameters.get(0);
                PsiType type = parameter.getType();
                if (type instanceof PsiClassType) {
                    PsiClass paramClass = ((PsiClassType) type).resolve();
                    if (paramClass != null && "android.view.View".equals(paramClass.getQualifiedName())) {
                        String methodName = method.getName();
                        PsiClass containingClass = method.getContainingClass();
                        if (containingClass != null) {
                            String qName = containingClass.getQualifiedName();
                            if (qName != null) {
                                validMethods.add(qName + "#" + methodName);
                                PsiClass superClass = containingClass.getSuperClass();
                                if (superClass != null) {
                                    String superQName = superClass.getQualifiedName();
                                    if (superQName != null) {
                                        superClasses.put(qName, superQName);
                                    }
                                }
                            }
                            validMethodNames.add(methodName);
                        }
                    }
                }
            }
        };
    }

    @Override
    public void afterCheckRootProject(Context context) {
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
                String currentClass = fqcn;
                while (currentClass != null) {
                    if (validMethods.contains(currentClass + "#" + methodName)) {
                        found = true;
                        break;
                    }
                    currentClass = superClasses.get(currentClass);
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
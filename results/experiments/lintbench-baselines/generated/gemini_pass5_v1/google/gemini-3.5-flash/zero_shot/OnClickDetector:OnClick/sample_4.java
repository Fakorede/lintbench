package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.*;
import com.intellij.psi.PsiType;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
import org.jetbrains.uast.UastUtils;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.*;

public class OnClickDetector extends Detector implements Detector.XmlScanner, Detector.SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "OnClick",
            "onClick method does not exist",
            "The `onClick` attribute value should be the name of a method in this View's context " +
            "to invoke when the view is clicked. This name must correspond to a public method " +
            "that takes exactly one parameter of type `View`.\n\n" +
            "Must be a string value, using '\\;' to escape characters such as '\\n' or " +
            "'\\uxxxx' for a unicode character.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(
                    OnClickDetector.class,
                    Scope.JAVA_AND_RESOURCE_FILES_SCOPE
            )
    );

    private final List<OnClickOccurrence> mOccurrences = new ArrayList<>();
    private final Map<String, Set<String>> mClassToMethods = new HashMap<>();
    private final Set<String> mAllMethods = new HashSet<>();

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mOccurrences.clear();
        mClassToMethods.clear();
        mAllMethods.clear();
    }

    @Nullable
    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_ON_CLICK);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value == null || value.isEmpty() || value.startsWith("@") || value.contains("{") || value.contains("}")) {
            return;
        }

        Element root = attribute.getOwnerDocument().getDocumentElement();
        String contextClass = null;
        if (root != null) {
            contextClass = root.getAttributeNS(SdkConstants.TOOLS_URI, SdkConstants.ATTR_CONTEXT);
            if (contextClass.isEmpty()) {
                contextClass = null;
            }
        }

        Location location = context.getValueLocation(attribute);
        mOccurrences.add(new OnClickOccurrence(value, location, contextClass));
    }

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UMethod.class);
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NonNull UMethod method) {
                if (!context.getEvaluator().isPublic(method)) {
                    return;
                }

                PsiType returnType = method.getReturnType();
                if (returnType == null || !returnType.equalsToText("void")) {
                    return;
                }

                List<UParameter> parameters = method.getUastParameters();
                if (parameters.size() != 1) {
                    return;
                }

                PsiType paramType = parameters.get(0).getType();
                if (!paramType.getCanonicalText().equals("android.view.View")) {
                    return;
                }

                String methodName = method.getName();
                mAllMethods.add(methodName);

                UClass containingClass = UastUtils.getContainingUClass(method);
                if (containingClass != null) {
                    String qualifiedName = containingClass.getQualifiedName();
                    if (qualifiedName != null) {
                        mClassToMethods.computeIfAbsent(qualifiedName, k -> new HashSet<>()).add(methodName);
                    }
                }
            }
        };
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (OnClickOccurrence occurrence : mOccurrences) {
            String methodName = occurrence.methodName;
            String contextClass = occurrence.contextClass;

            boolean found = false;
            if (contextClass != null) {
                for (Map.Entry<String, Set<String>> entry : mClassToMethods.entrySet()) {
                    String className = entry.getKey();
                    if (className != null) {
                        boolean matches = false;
                        if (className.equals(contextClass)) {
                            matches = true;
                        } else if (contextClass.startsWith(".")) {
                            if (className.endsWith(contextClass)) {
                                matches = true;
                            }
                        } else {
                            if (className.endsWith("." + contextClass)) {
                                matches = true;
                            }
                        }

                        if (matches && entry.getValue().contains(methodName)) {
                            found = true;
                            break;
                        }
                    }
                }
            }

            if (!found) {
                if (mAllMethods.contains(methodName)) {
                    found = true;
                }
            }

            if (!found) {
                context.report(
                        ISSUE,
                        occurrence.location,
                        String.format("Corresponding method `public void %s(View)` not found", methodName)
                );
            }
        }
    }

    private static class OnClickOccurrence {
        final String methodName;
        final Location location;
        final String contextClass;

        OnClickOccurrence(String methodName, Location location, String contextClass) {
            this.methodName = methodName;
            this.location = location;
            this.contextClass = contextClass;
        }
    }
}
package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
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
import org.w3c.dom.Node;

public class OnClickDetector extends Detector implements XmlScanner, SourceCodeScanner {
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String TOOLS_NS = "http://schemas.android.com/tools";
    private static final String ATTR_ON_CLICK = "onClick";
    private static final String ATTR_CONTEXT = "context";

    private final Map<String, List<Reference>> mReferences = new HashMap<>();
    private final Map<String, Set<String>> mHandlers = new HashMap<>();
    private final Map<String, String> mSuperClasses = new HashMap<>();

    public static final Issue ISSUE = Issue.create(
            "OnClick",
            "onClick method does not exist",
            "The `onClick` attribute value should be the name of a method in this View's context "
                    + "to invoke when the view is clicked. This name must correspond to a public "
                    + "method that takes exactly one parameter of type `View`.",
            Category.CORRECTNESS,
            8,
            Severity.ERROR,
            new Implementation(OnClickDetector.class, Scope.JAVA_AND_RESOURCE_FILES));

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mReferences.clear();
        mHandlers.clear();
        mSuperClasses.clear();
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_ON_CLICK);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!ANDROID_NS.equals(attribute.getNamespaceURI())) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || value.isEmpty() || value.startsWith("@")) {
            return;
        }

        String contextClass = findContextClass(context, attribute);
        mReferences.computeIfAbsent(value, k -> new ArrayList<>())
                .add(new Reference(context, attribute, contextClass));
    }

    private static String findContextClass(@NonNull XmlContext context, @NonNull Attr attribute) {
        Node node = attribute.getOwnerElement();
        while (node != null && node.getNodeType() != Node.DOCUMENT_NODE) {
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;

                String toolsContext = element.getAttributeNS(TOOLS_NS, ATTR_CONTEXT);
                if (toolsContext != null && !toolsContext.isEmpty()) {
                    return resolveContextClass(context, toolsContext);
                }

                String androidContext = element.getAttributeNS(ANDROID_NS, ATTR_CONTEXT);
                if (androidContext != null && !androidContext.isEmpty()) {
                    return resolveContextClass(context, androidContext);
                }
            }
            node = node.getParentNode();
        }
        return null;
    }

    private static String resolveContextClass(@NonNull XmlContext context, @NonNull String className) {
        if (className.startsWith(".")) {
            String pkg = context.getMainProject().getPackage();
            if (pkg != null && !pkg.isEmpty()) {
                return pkg + className;
            }
        }
        return className;
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UMethod.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NonNull UMethod node) {
                PsiMethod psiMethod = node.getJavaPsi();
                if (psiMethod == null) {
                    return;
                }
                PsiClass containingClass = psiMethod.getContainingClass();
                if (containingClass == null) {
                    return;
                }
                String className = containingClass.getQualifiedName();
                if (className == null) {
                    return;
                }

                if (isValidOnClickHandler(node)) {
                    mHandlers.computeIfAbsent(className, k -> new HashSet<>()).add(node.getName());
                }

                PsiClass superClass = containingClass.getSuperClass();
                if (superClass != null) {
                    String superName = superClass.getQualifiedName();
                    if (superName != null) {
                        mSuperClasses.put(className, superName);
                    }
                }
            }
        };
    }

    private static boolean isValidOnClickHandler(@NonNull UMethod method) {
        if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
            return false;
        }

        PsiType returnType = method.getReturnType();
        if (returnType == null || !PsiType.VOID.equals(returnType)) {
            return false;
        }

        List<UParameter> parameters = method.getUastParameters();
        if (parameters.size() != 1) {
            return false;
        }

        PsiType parameterType = parameters.get(0).getType();
        return parameterType != null && parameterType.equalsToText("android.view.View");
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, List<Reference>> entry : mReferences.entrySet()) {
            String name = entry.getKey();
            for (Reference reference : entry.getValue()) {
                if (!isHandlerFound(name, reference.contextClass)) {
                    reference.context.report(
                            ISSUE,
                            reference.attribute,
                            reference.context.getLocation(reference.attribute),
                            "Corresponding method handler '" + name + "' not found");
                }
            }
        }
    }

    private boolean isHandlerFound(@NonNull String name, String contextClass) {
        if (contextClass != null) {
            Set<String> visited = new HashSet<>();
            String current = contextClass;
            while (current != null && visited.add(current)) {
                Set<String> handlers = mHandlers.get(current);
                if (handlers != null && handlers.contains(name)) {
                    return true;
                }
                current = mSuperClasses.get(current);
            }
            return false;
        }

        for (Set<String> handlers : mHandlers.values()) {
            if (handlers.contains(name)) {
                return true;
            }
        }
        return false;
    }

    private static class Reference {
        final XmlContext context;
        final Attr attribute;
        final String contextClass;

        Reference(@NonNull XmlContext context, @NonNull Attr attribute, String contextClass) {
            this.context = context;
            this.attribute = attribute;
            this.contextClass = contextClass;
        }
    }
}
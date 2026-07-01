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
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.w3c.dom.Node;

public class OnClickDetector extends Detector implements XmlScanner, SourceCodeScanner {

    private static final String ATTR_ON_CLICK = "onClick";
    private static final String TOOLS_URI = "http://schemas.android.com/tools";
    private static final String VIEW_FQCN = "android.view.View";

    private List<AttrInfo> mAttrs;
    private Map<String, Set<String>> mMethods;
    private Map<String, String> mSuperClasses;

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
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_ON_CLICK);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value == null || value.isEmpty() || value.startsWith("@") || value.startsWith("?")) {
            return;
        }

        if (mAttrs == null) {
            mAttrs = new ArrayList<>();
        }

        String contextClass = getContextClass(context, attribute);
        mAttrs.add(new AttrInfo(context, attribute, value, contextClass));
    }

    private String getContextClass(@NonNull XmlContext context, @NonNull Attr attribute) {
        Node node = attribute.getOwnerElement();
        while (node != null && node.getNodeType() != Node.DOCUMENT_NODE) {
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                String ctx = element.getAttributeNS(TOOLS_URI, "context");
                if (ctx != null && !ctx.isEmpty()) {
                    if (ctx.startsWith(".")) {
                        String pkg = context.getMainProject().getPackage();
                        if (pkg != null && !pkg.isEmpty()) {
                            ctx = pkg + ctx;
                        }
                    }
                    return ctx;
                }
            }
            node = node.getParentNode();
        }
        return null;
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Arrays.asList(UMethod.class, UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NonNull UClass node) {
                String className = node.getQualifiedName();
                if (className == null) {
                    return;
                }
                PsiClass superClass = node.getSuperClass();
                if (superClass != null) {
                    String superName = superClass.getQualifiedName();
                    if (superName != null) {
                        if (mSuperClasses == null) {
                            mSuperClasses = new HashMap<>();
                        }
                        mSuperClasses.put(className, superName);
                    }
                }
            }

            @Override
            public void visitMethod(@NonNull UMethod node) {
                if (!isValidOnClickHandler(node)) {
                    return;
                }
                PsiClass containingClass = node.getContainingClass();
                if (containingClass == null) {
                    return;
                }
                String className = containingClass.getQualifiedName();
                if (className == null) {
                    return;
                }
                if (mMethods == null) {
                    mMethods = new HashMap<>();
                }
                mMethods.computeIfAbsent(className, k -> new HashSet<>()).add(node.getName());
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
        PsiType type = parameters.get(0).getType();
        return type != null && type.equalsToText(VIEW_FQCN);
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mAttrs == null) {
            return;
        }
        for (AttrInfo info : mAttrs) {
            if (!methodExists(info.name, info.contextClass)) {
                info.context.report(
                        ISSUE,
                        info.attribute,
                        info.context.getValueLocation(info.attribute),
                        "Corresponding method handler '" + info.name + "' not found");
            }
        }
    }

    private boolean methodExists(@NonNull String name, String contextClass) {
        if (contextClass != null) {
            Set<String> visited = new HashSet<>();
            String current = contextClass;
            while (current != null && visited.add(current)) {
                Set<String> methods = mMethods != null ? mMethods.get(current) : null;
                if (methods != null && methods.contains(name)) {
                    return true;
                }
                current = mSuperClasses != null ? mSuperClasses.get(current) : null;
            }
            return false;
        }

        if (mMethods != null) {
            for (Set<String> methods : mMethods.values()) {
                if (methods.contains(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static class AttrInfo {
        final XmlContext context;
        final Attr attribute;
        final String name;
        final String contextClass;

        AttrInfo(
                @NonNull XmlContext context,
                @NonNull Attr attribute,
                @NonNull String name,
                String contextClass) {
            this.context = context;
            this.attribute = attribute;
            this.name = name;
            this.contextClass = contextClass;
        }
    }
}
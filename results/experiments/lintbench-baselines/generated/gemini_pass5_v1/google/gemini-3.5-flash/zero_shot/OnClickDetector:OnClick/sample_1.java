package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.client.api.UastParser;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class OnClickDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "OnClick",
            "onClick method does not exist",
            "The `onClick` attribute value should be the name of a method in this View's context "
                    + "to invoke when the view is clicked. This name must correspond to a public "
                    + "method that takes exactly one parameter of type `View`.",
            Category.CORRECTNESS,
            10,
            Severity.ERROR,
            new Implementation(OnClickDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    @Nullable
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("onClick");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!"http://schemas.android.com/apk/res/android".equals(attribute.getNamespaceURI())) {
            return;
        }

        String methodName = attribute.getValue();
        if (methodName.isEmpty() || methodName.startsWith("@{") || methodName.startsWith("?")) {
            return;
        }

        Document document = attribute.getOwnerDocument();
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }

        String contextClass = root.getAttributeNS("http://schemas.android.com/tools", "context");
        if (contextClass.isEmpty() && "layout".equals(root.getTagName())) {
            NodeList children = root.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child instanceof Element) {
                    contextClass = ((Element) child).getAttributeNS("http://schemas.android.com/tools", "context");
                    if (!contextClass.isEmpty()) {
                        break;
                    }
                }
            }
        }

        if (contextClass.isEmpty()) {
            return;
        }

        if (contextClass.startsWith(".")) {
            String pkg = context.getProject().getPackage();
            if (pkg != null) {
                contextClass = pkg + contextClass;
            }
        }

        UastParser parser = context.getClient().getUastParser(context.getProject());
        JavaEvaluator evaluator = parser.getEvaluator();
        PsiClass psiClass = evaluator.findClass(contextClass);
        if (psiClass == null) {
            return;
        }

        PsiMethod[] methods = psiClass.findMethodsByName(methodName, true);
        if (methods.length == 0) {
            context.report(ISSUE, attribute, context.getLocation(attribute),
                    String.format("Corresponding method `public void %1$s(View)` not found in `%2$s`", methodName, contextClass));
            return;
        }

        boolean foundValid = false;
        for (PsiMethod method : methods) {
            if (!evaluator.isPublic(method)) {
                continue;
            }
            PsiParameter[] parameters = method.getParameterList().getParameters();
            if (parameters.length != 1) {
                continue;
            }
            PsiType paramType = parameters[0].getType();
            if (isViewType(paramType, evaluator)) {
                foundValid = true;
                break;
            }
        }

        if (!foundValid) {
            context.report(ISSUE, attribute, context.getLocation(attribute),
                    String.format("Method `%1$s` in `%2$s` has the wrong signature (must be `public void %1$s(View)`)", methodName, contextClass));
        }
    }

    private boolean isViewType(PsiType type, JavaEvaluator evaluator) {
        if (type == null) {
            return false;
        }
        if (type.equalsToText("android.view.View")) {
            return true;
        }
        PsiClass parameterClass = evaluator.getTypeClass(type);
        if (parameterClass != null) {
            return evaluator.inheritsFrom(parameterClass, "android.view.View", false);
        }
        return false;
    }
}
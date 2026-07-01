package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.ResourceUrl;
import com.android.resources.ResourceType;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.util.PsiUtil;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class OnClickDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "OnClick",
            "onClick method does not exist",
            "The `onClick` attribute value should be the name of a method in this View's context "
                    + "to invoke when the view is clicked. This name must correspond to a public "
                    + "method that takes exactly one parameter of type `View`.\n\n"
                    + "Must be a string value, using '\\\\' to escape characters such as '\\\\n' or "
                    + "'\\\\uxxxx' for a unicode character.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(OnClickDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_ON_CLICK);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }

        ResourceUrl url = ResourceUrl.parse(value);
        if (url != null && url.type == ResourceType.STRING && !url.framework) {
            String resolved = resolveStringResource(context, url);
            if (resolved == null || resolved.isEmpty()) {
                return;
            }
            value = resolved;
        }

        value = value.trim();
        if (value.isEmpty()) {
            return;
        }

        String className = resolveContextClassName(context, attribute.getOwnerElement());
        if (className == null || className.isEmpty()) {
            return;
        }

        JavaEvaluator evaluator = context.getEvaluator();
        if (evaluator == null) {
            return;
        }

        PsiClass cls = evaluator.findClass(className);
        if (cls == null) {
            return;
        }

        if (!hasMatchingMethod(cls, value)) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getValueLocation(attribute),
                    String.format("Corresponding method handler '%1$s' not found", value)
            );
        }
    }

    private static String resolveContextClassName(XmlContext context, Element element) {
        Node node = element;
        while (node != null) {
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element el = (Element) node;
                String contextClass = el.getAttributeNS(
                        SdkConstants.TOOLS_URI, SdkConstants.ATTR_CONTEXT);
                if (contextClass != null && !contextClass.isEmpty()) {
                    if (contextClass.startsWith(".")) {
                        String pkg = context.getProject().getPackage();
                        if (pkg != null) {
                            return pkg + contextClass;
                        }
                    }
                    return contextClass;
                }
            }
            node = node.getParentNode();
        }
        return null;
    }

    private static String resolveStringResource(XmlContext context, ResourceUrl url) {
        ResourceRepository repository = context.getProject().getResourceRepository();
        if (repository == null) {
            return null;
        }
        List<ResourceItem> items = repository.getResources(url.type, url.name);
        if (items != null) {
            for (ResourceItem item : items) {
                ResourceValue resourceValue = item.getResourceValue();
                if (resourceValue != null) {
                    String v = resourceValue.getValue();
                    if (v != null && !v.isEmpty()) {
                        return v;
                    }
                }
            }
        }
        return null;
    }

    private static boolean hasMatchingMethod(PsiClass cls, String methodName) {
        for (PsiMethod method : cls.findMethodsByName(methodName, true)) {
            if (!method.hasModifierProperty(PsiModifier.PUBLIC)
                    || method.hasModifierProperty(PsiModifier.STATIC)) {
                continue;
            }
            PsiParameterList params = method.getParameterList();
            if (params.getParametersCount() != 1) {
                continue;
            }
            PsiParameter param = params.getParameters()[0];
            PsiClass paramClass = PsiUtil.resolveClassInType(param.getType());
            if (paramClass != null
                    && SdkConstants.CLASS_VIEW.equals(paramClass.getQualifiedName())) {
                return true;
            }
        }
        return false;
    }
}
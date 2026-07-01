package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
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
import java.io.File;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

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
        String className = resolveContextClassName(context, attribute.getOwnerElement());
        if (className == null || className.isEmpty()) {
            return;
        }

        JavaEvaluator evaluator = context.getDriver().getJavaEvaluator();
        if (evaluator == null) {
            return;
        }

        PsiClass cls = evaluator.findClass(className);
        if (cls == null) {
            return;
        }

        String value = resolveAttributeValue(context, attribute.getValue());
        if (value == null) {
            return;
        }
        value = value.trim();

        if (value.isEmpty()) {
            reportMissing(context, attribute, value);
            return;
        }

        if (!hasMatchingMethod(cls, value)) {
            reportMissing(context, attribute, value);
        }
    }

    private static void reportMissing(XmlContext context, Attr attribute, String value) {
        context.report(
                ISSUE,
                attribute,
                context.getValueLocation(attribute),
                String.format("Corresponding method handler '%1$s' not found", value)
        );
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
                        if (pkg != null && !pkg.isEmpty()) {
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

    private static String resolveAttributeValue(XmlContext context, String value) {
        if (value == null || !value.startsWith("@")) {
            return value;
        }

        int slash = value.indexOf('/');
        if (slash <= 0 || slash >= value.length() - 1) {
            return value;
        }

        String type = value.substring(1, slash);
        int colon = type.indexOf(':');
        if (colon != -1) {
            type = type.substring(colon + 1);
        }

        if (!"string".equals(type)) {
            return value;
        }

        String name = value.substring(slash + 1);
        String resolved = resolveStringResource(context, name);
        return resolved != null ? resolved : value;
    }

    private static String resolveStringResource(XmlContext context, String name) {
        String value = resolveStringFromRepository(context, name);
        if (value != null) {
            return value;
        }
        return resolveStringFromFiles(context, name);
    }

    private static String resolveStringFromRepository(XmlContext context, String name) {
        try {
            Object project = context.getProject();
            Method method = findResourceRepositoryMethod(project.getClass());
            if (method == null) {
                return null;
            }

            Object repository;
            if (method.getParameterTypes().length == 0) {
                repository = method.invoke(project);
            } else {
                repository = method.invoke(project, Boolean.FALSE);
            }

            if (!(repository instanceof ResourceRepository)) {
                return null;
            }

            Method getResources = repository.getClass().getMethod(
                    "getResources", ResourceType.class, String.class);
            @SuppressWarnings("unchecked")
            List<ResourceItem> items =
                    (List<ResourceItem>) getResources.invoke(repository, ResourceType.STRING, name);
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
        } catch (Throwable ignored) {
            // Fall back to file scanning.
        }
        return null;
    }

    private static Method findResourceRepositoryMethod(Class<?> clazz) {
        try {
            return clazz.getMethod("getResourceRepository");
        } catch (NoSuchMethodException ignored) {
        }
        try {
            return clazz.getMethod("getResourceRepository", boolean.class);
        } catch (NoSuchMethodException ignored) {
        }
        return null;
    }

    private static String resolveStringFromFiles(XmlContext context, String name) {
        for (File resDir : context.getProject().getResourceFolders()) {
            File[] typeDirs = resDir.listFiles();
            if (typeDirs == null) {
                continue;
            }
            for (File dir : typeDirs) {
                if (!dir.isDirectory() || !dir.getName().startsWith("values")) {
                    continue;
                }
                File[] xmlFiles = dir.listFiles((file, fileName) -> fileName.endsWith(".xml"));
                if (xmlFiles == null) {
                    continue;
                }
                for (File xml : xmlFiles) {
                    try {
                        String value = findStringInFile(xml, name);
                        if (value != null) {
                            return value;
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return null;
    }

    private static String findStringInFile(File file, String name) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setValidating(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(file);
        NodeList nodes = doc.getElementsByTagName("string");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            if (name.equals(element.getAttribute("name"))) {
                String text = element.getTextContent();
                if (text != null) {
                    return text;
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
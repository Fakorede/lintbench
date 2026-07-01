package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class OnClickDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    private static final String ACTIVITY_CLASS = "android.app.Activity";
    private static final String VIEW_CLASS = "android.view.View";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String TOOLS_URI = "http://schemas.android.com/tools";
    private static final String ATTR_ON_CLICK = "onClick";
    private static final String ATTR_CONTEXT = "context";

    private static final Implementation IMPLEMENTATION =
            new Implementation(OnClickDetector.class, Scope.JAVA_AND_RESOURCE_FILES);

    public static final Issue ISSUE =
            Issue.create(
                    "OnClick",
                    "Corresponding method handler not found",
                    "The `@android:onClick` attribute value must name a method in the view's "
                            + "context that is invoked when the view is clicked. This method must "
                            + "be `public`, take exactly one `View` parameter, and be defined "
                            + "in the activity class referenced by `tools:context`.",
                    Category.CORRECTNESS,
                    7,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private final Map<String, List<Location>> mPending = new HashMap<>();

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_ON_CLICK);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }

        String className = getContextClass(context, attribute);
        if (className == null) {
            return;
        }

        String key = className + "#" + value;
        List<Location> locations = mPending.get(key);
        if (locations == null) {
            locations = new ArrayList<>();
            mPending.put(key, locations);
        }
        locations.add(context.getLocation(attribute));
    }

    private String getContextClass(XmlContext context, Attr attribute) {
        Node node = attribute.getOwnerElement();
        String className = null;
        while (node != null && node.getNodeType() == Node.ELEMENT_NODE) {
            Element element = (Element) node;
            className = element.getAttributeNS(TOOLS_URI, ATTR_CONTEXT);
            if (className != null && !className.isEmpty()) {
                break;
            }
            node = element.getParentNode();
        }

        if (className == null || className.isEmpty()) {
            return null;
        }

        if (className.startsWith(".")) {
            String pkg = context.getProject().getPackage();
            if (pkg != null && !pkg.isEmpty()) {
                className = pkg + className;
            }
        }

        return className;
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(ACTIVITY_CLASS);
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        String className = declaration.getQualifiedName();
        if (className == null) {
            return;
        }

        List<String> resolved = new ArrayList<>();
        for (Map.Entry<String, List<Location>> entry : mPending.entrySet()) {
            String key = entry.getKey();
            int index = key.indexOf('#');
            if (index < 0) {
                continue;
            }
            String contextClass = key.substring(0, index);
            if (!contextClass.equals(className)) {
                continue;
            }
            String methodName = key.substring(index + 1);
            if (hasOnClickMethod(declaration, methodName)) {
                resolved.add(key);
            }
        }

        for (String key : resolved) {
            mPending.remove(key);
        }
    }

    private boolean hasOnClickMethod(UClass cls, String methodName) {
        PsiClass psiClass = cls.getJavaPsi();
        while (psiClass != null) {
            for (PsiMethod method : psiClass.getMethods()) {
                if (methodName.equals(method.getName()) && isOnClickHandler(method)) {
                    return true;
                }
            }
            psiClass = psiClass.getSuperClass();
        }
        return false;
    }

    private boolean isOnClickHandler(PsiMethod method) {
        if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
            return false;
        }

        PsiParameterList parameterList = method.getParameterList();
        if (parameterList.getParametersCount() != 1) {
            return false;
        }

        PsiParameter parameter = parameterList.getParameters()[0];
        PsiType type = parameter.getType();

        String canonical = type.getCanonicalText();
        if (VIEW_CLASS.equals(canonical)) {
            return true;
        }

        if (type instanceof PsiClassType) {
            PsiClass resolved = ((PsiClassType) type).resolve();
            if (resolved != null && VIEW_CLASS.equals(resolved.getQualifiedName())) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void afterCheckRootProject(Context context) {
        for (Map.Entry<String, List<Location>> entry : mPending.entrySet()) {
            String key = entry.getKey();
            int index = key.indexOf('#');
            String className = key.substring(0, index);
            String methodName = key.substring(index + 1);

            String message =
                    String.format(
                            "Corresponding method handler '%1$s(View)' not found in class '%2$s'",
                            methodName, className);

            for (Location location : entry.getValue()) {
                context.report(ISSUE, location, message);
            }
        }
    }
}
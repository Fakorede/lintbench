package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiModifier;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

public class OnClickDetector extends LayoutDetector {

    public static final Issue ISSUE = Issue.create(
            "OnClick",
            "`onClick` method does not exist",
            "The `onClick` attribute value should be the name of a method in this View's context " +
            "to invoke when the view is clicked. This name must correspond to a public method " +
            "that takes exactly one parameter of type `View`.\n\n" +
            "Must be a string value, using '\\\\;' to escape characters such as '\\\\n' or " +
            "'\\\\uxxxx' for a unicode character.",
            Category.CORRECTNESS, 5, Severity.ERROR,
            new Implementation(OnClickDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public List<String> getApplicableAttributes() {
        return Collections.singletonList(SdkConstants.ATTR_ON_CLICK);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String methodName = attribute.getValue();
        if (methodName == null || methodName.isEmpty()) {
            return;
        }

        List<String> candidateClasses = getActivityClasses(context);
        boolean methodFound = false;

        for (String className : candidateClasses) {
            UClass uClass = context.getClient().findClass(className);
            if (uClass != null && hasValidOnClickMethod(uClass, methodName)) {
                methodFound = true;
                break;
            }
        }

        if (!methodFound) {
            context.report(ISSUE, attribute, context.getValueLocation(attribute),
                    "Corresponding method handler `" + methodName + "(android.view.View)` not found");
        }
    }

    private boolean hasValidOnClickMethod(UClass uClass, String methodName) {
        for (UMethod method : uClass.getMethods()) {
            if (!methodName.equals(method.getName())) {
                continue;
            }
            if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
                continue;
            }
            if (method.getReturnType() == null || !method.getReturnType().getCanonicalText().equals("void")) {
                continue;
            }
            List<UParameter> parameters = method.getUastParameters();
            if (parameters.size() != 1) {
                continue;
            }
            String paramType = parameters.get(0).getType().getCanonicalText();
            if ("android.view.View".equals(paramType)) {
                return true;
            }
        }
        return false;
    }

    private List<String> getActivityClasses(XmlContext context) {
        List<String> activities = new ArrayList<>();
        try {
            File manifestFile = context.getProject().getManifest();
            if (manifestFile == null || !manifestFile.exists()) {
                return activities;
            }
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifestFile);
            Element manifest = doc.getDocumentElement();
            String pkg = manifest.getAttribute("package");

            NodeList appNodes = manifest.getElementsByTagName("application");
            if (appNodes.getLength() > 0) {
                Element app = (Element) appNodes.item(0);
                NodeList activityNodes = app.getElementsByTagName("activity");
                for (int i = 0; i < activityNodes.getLength(); i++) {
                    Element activity = (Element) activityNodes.item(i);
                    String name = activity.getAttribute("android:name");
                    if (name != null && !name.isEmpty()) {
                        if (name.startsWith(".")) {
                            name = pkg + name;
                        } else if (!name.contains(".")) {
                            name = pkg + "." + name;
                        }
                        activities.add(name);
                    }
                }
            }
        } catch (Exception ignored) {
            // Gracefully handle manifest parsing failures
        }
        return activities;
    }
}
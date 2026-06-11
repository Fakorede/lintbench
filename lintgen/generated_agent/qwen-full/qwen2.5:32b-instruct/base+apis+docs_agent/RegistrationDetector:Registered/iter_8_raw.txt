package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import org.jetbrains.uast.UClass;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class RegistrationDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "UnregisteredComponents",
            "Activities, services and content providers should be registered in the AndroidManifest.xml file using <activity>, <service> and <provider> tags.",
            "If your activity is simply a parent class intended to be subclassed by other \"real\" activities, make it an abstract class.",
            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            new Implementation(
                    RegistrationDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    );

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.emptyList();
    }

    @Override
    public void visitClass(JavaContext context, UClass node) {
        if (!node.isAbstract()) {
            String className = node.getQualifiedName();
            boolean isActivityOrServiceOrProvider = isActivity(node) || isService(node) || isContentProvider(node);
            if (isActivityOrServiceOrProvider && !isComponentRegistered(context, className)) {
                Location location = context.getLocation(node.getSourcePsi());
                context.report(ISSUE, node, location, "Component class %s is not registered in the manifest", className);
            }
        }
    }

    private boolean isActivity(UClass uClass) {
        return uClass.getContainingClass() == null && uClass.isInheritedFrom("android.app.Activity");
    }

    private boolean isService(UClass uClass) {
        return uClass.getContainingClass() == null && uClass.isInheritedFrom("android.app.Service");
    }

    private boolean isContentProvider(UClass uClass) {
        return uClass.getContainingClass() == null && uClass.isInheritedFrom("android.content.ContentProvider");
    }

    private boolean isComponentRegistered(JavaContext context, String className) {
        Document manifest = context.getProject().getModuleManifest();
        if (manifest == null) {
            return false;
        }
        NodeList applicationNodes = manifest.getElementsByTagName("application");
        for (int i = 0; i < applicationNodes.getLength(); i++) {
            Element applicationNode = (Element) applicationNodes.item(i);
            List<String> registeredComponents = new ArrayList<>();
            addRegisteredComponents(registeredComponents, applicationNode.getElementsByTagName("activity"));
            addRegisteredComponents(registeredComponents, applicationNode.getElementsByTagName("service"));
            addRegisteredComponents(registeredComponents, applicationNode.getElementsByTagName("provider"));

            if (registeredComponents.contains(className)) {
                return true;
            }
        }
        return false;
    }

    private void addRegisteredComponents(List<String> registeredComponents, NodeList nodeList) {
        for (int i = 0; i < nodeList.getLength(); i++) {
            Element componentElement = (Element) nodeList.item(i);
            String componentName = componentElement.getAttribute("android:name");
            if (!componentName.isEmpty()) {
                registeredComponents.add(componentName);
            }
        }
    }

}
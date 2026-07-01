package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.*;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UastModifier;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RegistrationDetector extends Detector implements Detector.UastScanner {

    public static final Issue ISSUE = Issue.create(
        "Registered",
        "Class is not registered in the manifest",
        "Activities, services and content providers should be registered in the " +
        "`AndroidManifest.xml` file using `<activity>`, `<service>` and " +
        "`<provider>` tags.\n\n" +
        "If your activity is simply a parent class intended to be " +
        "subclassed by other \"real\" activities, make it an abstract class.",
        Category.CORRECTNESS,
        6,
        Severity.WARNING,
        new Implementation(RegistrationDetector.class, Scope.JAVA_FILE_SCOPE));

    private Set<String> registeredComponents;

    @Override
    public void beforeCheckProject(Context context) {
        registeredComponents = new HashSet<>();
        File manifest = context.getMainProject().getManifest();
        if (manifest == null || !manifest.exists()) {
            return;
        }

        try {
            Document doc = XmlParser.parse(manifest);
            if (doc == null) return;
            Element root = doc.getDocumentElement();
            if (root == null) return;

            String pkg = root.getAttribute("package");
            NodeList apps = root.getElementsByTagName("application");
            for (int i = 0; i < apps.getLength(); i++) {
                Element app = (Element) apps.item(i);
                collectComponents(app, pkg, "activity");
                collectComponents(app, pkg, "service");
                collectComponents(app, pkg, "provider");
            }
        } catch (Exception e) {
            // Ignore manifest parsing errors
        }
    }

    private void collectComponents(Element parent, String pkg, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        for (int i = 0; i < nodes.getLength(); i++) {
            Element el = (Element) nodes.item(i);
            String name = el.getAttributeNS(SdkConstants.ANDROID_URI, "name");
            if (name != null && !name.isEmpty()) {
                registeredComponents.add(resolveClassName(pkg, name));
            }
        }
    }

    private static String resolveClassName(String pkg, String name) {
        if (name.startsWith(".")) {
            return pkg + name;
        }
        if (name.indexOf('.') == -1) {
            return pkg + "." + name;
        }
        return name;
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(UClass node) {
                if (node.hasModifier(UastModifier.ABSTRACT)) {
                    return;
                }
                String qualifiedName = node.getQualifiedName();
                if (qualifiedName == null) {
                    return;
                }
                // Skip non-static inner classes as they cannot be Android components
                if (node.getUastParent() instanceof UClass && !node.hasModifier(UastModifier.STATIC)) {
                    return;
                }

                JavaEvaluator evaluator = context.getEvaluator();
                boolean isComponent = evaluator.extendsClass(node, "android.app.Activity", false) ||
                                      evaluator.extendsClass(node, "android.app.Service", false) ||
                                      evaluator.extendsClass(node, "android.content.ContentProvider", false);

                if (isComponent && !registeredComponents.contains(qualifiedName)) {
                    context.report(ISSUE, node, context.getNameLocation(node),
                        "This class is not registered in the manifest");
                }
            }
        };
    }
}
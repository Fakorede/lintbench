package com.android.tools.lint.checks;

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

import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class RegistrationDetector extends Detector implements SourceCodeScanner {

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
            Severity.ERROR,
            new Implementation(RegistrationDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private final Set<String> registeredComponents = new HashSet<>();

    @Override
    public void beforeCheckProject(Context context) {
        registeredComponents.clear();
        File manifest = context.getProject().getManifest();
        if (manifest == null || !manifest.exists()) {
            return;
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(manifest);
            Element root = document.getDocumentElement();
            if (root == null || !root.getTagName().equals("manifest")) {
                return;
            }

            String pkg = root.getAttribute("package");
            if (pkg == null) {
                pkg = "";
            }

            Element application = null;
            NodeList rootChildren = root.getChildNodes();
            for (int i = 0; i < rootChildren.getLength(); i++) {
                Node n = rootChildren.item(i);
                if (n.getNodeType() == Node.ELEMENT_NODE && n.getNodeName().equals("application")) {
                    application = (Element) n;
                    break;
                }
            }

            if (application == null) {
                return;
            }

            NodeList appChildren = application.getChildNodes();
            for (int i = 0; i < appChildren.getLength(); i++) {
                Node n = appChildren.item(i);
                if (n.getNodeType() == Node.ELEMENT_NODE) {
                    Element child = (Element) n;
                    String tag = child.getTagName();
                    if (tag.equals("activity") || tag.equals("service") ||
                        tag.equals("provider") || tag.equals("receiver")) {
                        String name = child.getAttributeNS(ANDROID_URI, "name");
                        if (name != null && !name.isEmpty()) {
                            registeredComponents.add(resolveClassName(pkg, name));
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore manifest parsing errors
        }
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
                if (context.getEvaluator().isAbstract(node) || node.isInterface()) {
                    return;
                }

                String qualifiedName = node.getQualifiedName();
                if (qualifiedName == null) {
                    return;
                }

                boolean isComponent = context.getEvaluator().extendsClass(node, "android.app.Activity", false)
                        || context.getEvaluator().extendsClass(node, "android.app.Service", false)
                        || context.getEvaluator().extendsClass(node, "android.content.ContentProvider", false);

                if (isComponent && !registeredComponents.contains(qualifiedName)) {
                    context.report(ISSUE, node, context.getLocation(node),
                            "Class is not registered in the manifest");
                }
            }
        };
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
}
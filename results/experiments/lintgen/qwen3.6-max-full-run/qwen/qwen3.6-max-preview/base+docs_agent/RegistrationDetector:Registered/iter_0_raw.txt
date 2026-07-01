package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.UElementHandler;

import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.Collections;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class RegistrationDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
        "Registered",
        "Class is not registered in the manifest",
        "Activities, services and content providers should be registered in the " +
        "`AndroidManifest.xml` file using `<activity>`, `<service>` and `<provider>` tags.\n\n" +
        "If your activity is simply a parent class intended to be subclassed by other \"real\" " +
        "activities, make it an abstract class.",
        Category.CORRECTNESS,
        6,
        Severity.WARNING,
        new Implementation(RegistrationDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(UClass node) {
                if (node.isAbstract()) {
                    return;
                }

                String qualifiedName = node.getQualifiedName();
                if (qualifiedName == null) {
                    return;
                }

                boolean isComponent = context.getEvaluator().extendsClass(node, "android.app.Activity", false)
                        || context.getEvaluator().extendsClass(node, "android.app.Service", false)
                        || context.getEvaluator().extendsClass(node, "android.content.ContentProvider", false);

                if (!isComponent) {
                    return;
                }

                if (!isRegistered(context, qualifiedName)) {
                    context.report(ISSUE, context.getNameLocation(node),
                            "Class is not registered in the manifest");
                }
            }
        };
    }

    private static boolean isRegistered(JavaContext context, String qualifiedName) {
        File manifest = context.getProject().getManifest();
        if (manifest == null || !manifest.exists()) {
            return false;
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(manifest);
            Element root = doc.getDocumentElement();
            if (root == null) {
                return false;
            }

            String pkg = root.getAttribute("package");
            String[] tags = {"activity", "service", "provider"};
            String androidNs = "http://schemas.android.com/apk/res/android";

            for (String tag : tags) {
                NodeList nodes = root.getElementsByTagName(tag);
                for (int i = 0; i < nodes.getLength(); i++) {
                    Element el = (Element) nodes.item(i);
                    String name = el.getAttributeNS(androidNs, "name");
                    if (name != null && !name.isEmpty()) {
                        String fullName = name;
                        if (name.startsWith(".")) {
                            fullName = pkg + name;
                        } else if (!name.contains(".")) {
                            fullName = pkg + "." + name;
                        }
                        if (fullName.equals(qualifiedName)) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Manifest parsing errors are reported by other lint checks
        }
        return false;
    }
}
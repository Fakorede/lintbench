package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.UElementHandler;
import com.android.utils.XmlUtils;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.FileReader;
import java.util.Collections;
import java.util.List;

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
            Severity.ERROR,
            new Implementation(RegistrationDetector.class, Scope.JAVA_FILE_SCOPE));

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(UClass node) {
                JavaEvaluator evaluator = context.getEvaluator();
                if (evaluator.isAbstract(node) || node.isInterface() || node.isAnnotationType()) {
                    return;
                }

                String qualifiedName = node.getQualifiedName();
                if (qualifiedName == null) return;

                boolean isActivity = evaluator.extendsClass(node, "android.app.Activity", false);
                boolean isService = evaluator.extendsClass(node, "android.app.Service", false);
                boolean isProvider = evaluator.extendsClass(node, "android.content.ContentProvider", false);

                if (!isActivity && !isService && !isProvider) {
                    return;
                }

                if (!isRegistered(context, qualifiedName)) {
                    String type = isActivity ? "Activity" : (isService ? "Service" : "ContentProvider");
                    context.report(ISSUE, node, context.getNameLocation(node),
                            "The " + type + " " + node.getName() + " is not registered in the manifest");
                }
            }
        };
    }

    private boolean isRegistered(JavaContext context, String className) {
        File manifest = context.getProject().getManifestFile();
        if (manifest == null || !manifest.exists()) {
            return false;
        }

        try {
            Document document = XmlUtils.parseDocument(new FileReader(manifest), true);
            Element root = document.getDocumentElement();
            if (root == null) return false;

            NodeList applications = root.getElementsByTagName("application");
            if (applications.getLength() == 0) return false;

            Element application = (Element) applications.item(0);
            String[] tags = {"activity", "service", "provider"};
            String shortName = className.substring(className.lastIndexOf('.') + 1);
            String pkg = context.getProject().getPackageName();

            for (String tag : tags) {
                NodeList nodes = application.getElementsByTagName(tag);
                for (int i = 0; i < nodes.getLength(); i++) {
                    Element element = (Element) nodes.item(i);
                    String name = element.getAttribute("android:name");
                    if (name == null || name.isEmpty()) continue;

                    if (name.equals(className)) return true;
                    if (name.equals(shortName)) return true;
                    if (name.startsWith(".")) {
                        if (pkg != null && (pkg + name).equals(className)) return true;
                    } else if (!name.contains(".")) {
                        if (pkg != null && (pkg + "." + name).equals(className)) return true;
                    }
                }
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }
        return false;
    }
}
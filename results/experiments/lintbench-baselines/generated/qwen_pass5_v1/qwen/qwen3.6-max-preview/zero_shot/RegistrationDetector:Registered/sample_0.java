package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.*;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UastModifier;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.*;

public class RegistrationDetector extends Detector implements UastScanner {

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
            new Implementation(RegistrationDetector.class, Scope.JAVA_FILE_SCOPE, Scope.MANIFEST_SCOPE)
    );

    private final List<Candidate> candidates = new ArrayList<>();

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public void visitClass(JavaContext context, UClass node) {
        if (node.getModifiers().contains(UastModifier.ABSTRACT)) {
            return;
        }

        String qualifiedName = node.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }

        JavaEvaluator evaluator = context.getEvaluator();
        boolean isActivity = evaluator.extendsClass(node, "android.app.Activity", false);
        boolean isService = evaluator.extendsClass(node, "android.app.Service", false);
        boolean isProvider = evaluator.extendsClass(node, "android.content.ContentProvider", false);

        if (isActivity || isService || isProvider) {
            candidates.add(new Candidate(qualifiedName, context.getLocation(node)));
        }
    }

    @Override
    public void afterCheckProject(Context context) {
        if (candidates.isEmpty()) {
            return;
        }

        File manifest = context.getProject().getManifest();
        if (manifest == null || !manifest.exists()) {
            return;
        }

        Set<String> registered = new HashSet<>();
        try {
            Document document = XmlUtils.parseDocument(manifest, true);
            Element root = document.getDocumentElement();
            if (root == null) {
                return;
            }

            String packageName = root.getAttribute("package");
            if (packageName == null) {
                packageName = "";
            }

            NodeList applicationNodes = root.getElementsByTagName("application");
            for (int i = 0; i < applicationNodes.getLength(); i++) {
                Element application = (Element) applicationNodes.item(i);
                collectComponents(application, "activity", packageName, registered);
                collectComponents(application, "service", packageName, registered);
                collectComponents(application, "provider", packageName, registered);
            }
        } catch (Exception e) {
            // Ignore parsing errors; manifest might be malformed or unreadable
        }

        for (Candidate candidate : candidates) {
            String name = candidate.qualifiedName;
            String normalized = name.replace('$', '.');
            if (!registered.contains(name) && !registered.contains(normalized)) {
                context.report(ISSUE, candidate.location, "Class is not registered in the manifest");
            }
        }
        candidates.clear();
    }

    private static void collectComponents(Element parent, String tagName, String packageName, Set<String> registered) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            String name = element.getAttributeNS(SdkConstants.ANDROID_URI, "name");
            if (name != null && !name.isEmpty()) {
                registered.add(resolveClassName(name, packageName));
            }
        }
    }

    private static String resolveClassName(String name, String packageName) {
        if (name.startsWith(".")) {
            return packageName + name;
        } else if (name.indexOf('.') == -1) {
            return packageName.isEmpty() ? name : packageName + "." + name;
        }
        return name;
    }

    private static class Candidate {
        final String qualifiedName;
        final Location location;

        Candidate(String qualifiedName, Location location) {
            this.qualifiedName = qualifiedName;
            this.location = location;
        }
    }
}
package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import com.intellij.psi.PsiModifier;
import org.jetbrains.uast.UClass;
import org.w3c.dom.*;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
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
            Severity.WARNING,
            new Implementation(RegistrationDetector.class, Scope.JAVA_FILE_SCOPE)
    );

    private Map<File, Set<String>> manifestCache = new HashMap<>();

    @Override
    public void afterCheckProject(Context context) {
        manifestCache.clear();
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.app.Activity",
                "android.app.Service",
                "android.content.ContentProvider"
        );
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        if (declaration.hasModifier(PsiModifier.ABSTRACT)) {
            return;
        }
        if (context.isTestSource()) {
            return;
        }
        if (declaration.getContainingClass() != null) {
            return;
        }

        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }

        if (!isRegistered(context, qualifiedName)) {
            context.report(ISSUE, declaration, context.getNameLocation(declaration),
                    "Class is not registered in the manifest");
        }
    }

    private boolean isRegistered(JavaContext context, String className) {
        File manifest = context.getProject().getManifest();
        if (manifest == null || !manifest.exists()) {
            return false;
        }

        Set<String> registered;
        synchronized (manifestCache) {
            registered = manifestCache.get(manifest);
            if (registered == null) {
                registered = parseManifest(manifest);
                manifestCache.put(manifest, registered);
            }
        }

        return registered.contains(className);
    }

    private Set<String> parseManifest(File manifest) {
        Set<String> registered = new HashSet<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setNamespaceAware(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(manifest);
            Element root = doc.getDocumentElement();
            String pkg = root.getAttribute("package");
            if (pkg == null) {
                pkg = "";
            }

            NodeList appNodes = root.getElementsByTagName("application");
            if (appNodes.getLength() > 0) {
                Element app = (Element) appNodes.item(0);
                String[] tags = {"activity", "service", "provider", "receiver"};
                for (String tag : tags) {
                    NodeList nodes = app.getElementsByTagName(tag);
                    for (int i = 0; i < nodes.getLength(); i++) {
                        Element elem = (Element) nodes.item(i);
                        String name = elem.getAttribute("android:name");
                        if (name != null && !name.isEmpty()) {
                            registered.add(resolveClassName(pkg, name));
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore parsing errors; lint will handle malformed manifests elsewhere
        }
        return registered;
    }

    private String resolveClassName(String pkg, String name) {
        if (name.startsWith(".")) {
            return pkg + name;
        } else if (name.indexOf('.') == -1) {
            return pkg + "." + name;
        }
        return name;
    }
}
package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiModifier;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RegistrationDetector extends Detector implements XmlScanner, SourceCodeScanner {

    private static final String KEY_REGISTERED = "RegistrationDetector.registered";

    public static final Issue ISSUE = Issue.create(
            "Registered",
            "Class is not registered in the manifest",
            "Activities, services and content providers should be registered in the `AndroidManifest.xml` file using `<activity>`, `<service>` and `<provider>` tags. If your activity is simply a parent class intended to be subclassed by other \"real\" activities, make it an abstract class.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    RegistrationDetector.class,
                    EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)
            )
    );

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("activity", "service", "provider");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
        if (name == null || name.isEmpty()) {
            return;
        }
        Document document = element.getOwnerDocument();
        Element root = document.getDocumentElement();
        String pkg = root != null ? root.getAttribute("package") : "";
        if (pkg.isEmpty()) {
            pkg = context.getProject().getPackage();
        }
        if (pkg == null) {
            pkg = "";
        }

        String fqn;
        if (name.startsWith(".")) {
            fqn = pkg + name;
        } else if (!name.contains(".")) {
            fqn = pkg + "." + name;
        } else {
            fqn = name;
        }

        Map<String, Boolean> registered = (Map<String, Boolean>) context.getDriver().getProperty(KEY_REGISTERED);
        if (registered == null) {
            registered = new HashMap<>();
            context.getDriver().putProperty(KEY_REGISTERED, registered);
        }
        registered.put(fqn, Boolean.TRUE);
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
                if (node.isInterface()) {
                    return;
                }
                if (node.hasModifierProperty(PsiModifier.ABSTRACT)) {
                    return;
                }

                boolean isActivity = context.getEvaluator().inheritsFrom(node, "android.app.Activity", false);
                boolean isService = context.getEvaluator().inheritsFrom(node, "android.app.Service", false);
                boolean isProvider = context.getEvaluator().inheritsFrom(node, "android.content.ContentProvider", false);

                if (isActivity || isService || isProvider) {
                    String fqn = node.getQualifiedName();
                    if (fqn == null) {
                        return;
                    }

                    Map<String, Boolean> registered = (Map<String, Boolean>) context.getDriver().getProperty(KEY_REGISTERED);
                    if (registered == null) {
                        registered = new HashMap<>();
                        context.getDriver().putProperty(KEY_REGISTERED, registered);
                        
                        List<File> manifestFiles = context.getProject().getManifestFiles();
                        for (File manifestFile : manifestFiles) {
                            try {
                                Document doc = context.getClient().getXmlParser().parseXml(manifestFile);
                                if (doc != null) {
                                    fillRegisteredMap(doc, registered, context.getProject().getPackage());
                                }
                            } catch (Exception e) {
                                // Ignore parsing errors
                            }
                        }
                    }

                    if (!registered.containsKey(fqn)) {
                        context.report(
                                ISSUE,
                                node,
                                context.getNameLocation(node),
                                "The class `" + fqn + "` is not registered in the manifest"
                        );
                    }
                }
            }
        };
    }

    private void fillRegisteredMap(Document doc, Map<String, Boolean> registered, String defaultPackage) {
        Element root = doc.getDocumentElement();
        if (root == null) return;
        String pkg = root.getAttribute("package");
        if (pkg == null || pkg.isEmpty()) {
            pkg = defaultPackage;
        }
        if (pkg == null) {
            pkg = "";
        }

        String[] tags = {"activity", "service", "provider"};
        for (String tag : tags) {
            NodeList list = doc.getElementsByTagName(tag);
            for (int i = 0; i < list.getLength(); i++) {
                Element element = (Element) list.item(i);
                String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
                if (name == null || name.isEmpty()) {
                    name = element.getAttribute("android:name");
                }
                if (name != null && !name.isEmpty()) {
                    String fqn;
                    if (name.startsWith(".")) {
                        fqn = pkg + name;
                    } else if (!name.contains(".")) {
                        fqn = pkg + "." + name;
                    } else {
                        fqn = name;
                    }
                    registered.put(fqn, Boolean.TRUE);
                }
            }
        }
    }
}
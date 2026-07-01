package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.PartialResult;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class RegistrationDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(RegistrationDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
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
                    IMPLEMENTATION);

    private static class ComponentInfo {
        final String name;
        final Location location;
        final JavaContext context;

        ComponentInfo(String name, Location location, JavaContext context) {
            this.name = name;
            this.location = location;
            this.context = context;
        }
    }

    private final List<ComponentInfo> mComponents = new ArrayList<>();

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.app.Activity",
                "android.app.Service",
                "android.content.ContentProvider",
                "android.content.BroadcastReceiver"
        );
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (declaration.isAbstract()) {
            return;
        }
        String fqn = declaration.getQualifiedName();
        if (fqn == null) {
            return;
        }
        // Skip test classes
        if (fqn.contains(".test.") || fqn.contains("androidTest") || fqn.endsWith("Test")) {
            return;
        }
        mComponents.add(new ComponentInfo(fqn, context.getLocation(declaration), context));
    }

    @Override
    public void checkPartialResults(
            @NonNull Context context, @NonNull PartialResult partialResults) {
        if (mComponents.isEmpty()) {
            return;
        }

        File manifest = context.getProject().getManifest();
        if (manifest == null || !manifest.exists()) {
            return;
        }

        Set<String> registered = new HashSet<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(manifest);
            String pkg = doc.getDocumentElement().getAttribute("package");

            NodeList nodes = doc.getElementsByTagName("*");
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                String tag = node.getNodeName();
                if (tag.equals("activity") || tag.equals("service") ||
                    tag.equals("provider") || tag.equals("receiver") ||
                    tag.equals("application")) {
                    NamedNodeMap attrs = node.getAttributes();
                    Node nameAttr = attrs.getNamedItemNS("http://schemas.android.com/apk/res/android", "name");
                    if (nameAttr != null) {
                        String name = nameAttr.getNodeValue();
                        if (name != null) {
                            if (name.startsWith(".")) {
                                name = pkg + name;
                            } else if (!name.contains(".")) {
                                name = pkg + "." + name;
                            }
                            registered.add(name);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore XML parsing errors; manifest might be malformed or unreadable
        }

        for (ComponentInfo info : mComponents) {
            if (!registered.contains(info.name)) {
                info.context.report(ISSUE, info.location,
                        "Class is not registered in the manifest");
            }
        }
        mComponents.clear();
    }
}
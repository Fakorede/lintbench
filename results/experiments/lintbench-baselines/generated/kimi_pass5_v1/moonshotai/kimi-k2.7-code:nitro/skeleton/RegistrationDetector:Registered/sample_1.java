package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.PartialResult;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import java.io.File;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class RegistrationDetector extends LayoutDetector {

    private static final String ANDROID_APP_ACTIVITY = "android.app.Activity";
    private static final String ANDROID_APP_SERVICE = "android.app.Service";
    private static final String ANDROID_CONTENT_PROVIDER = "android.content.ContentProvider";

    private static final Implementation IMPLEMENTATION =
            new Implementation(RegistrationDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "Registered",
                    "Class is not registered in the manifest",
                    "Activities, services and content providers must be registered in the AndroidManifest.xml file using <activity>, <service> and <provider> tags. If a class is intended only as a base class for other components, declare it abstract to suppress this warning.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                ANDROID_APP_ACTIVITY,
                ANDROID_APP_SERVICE,
                ANDROID_CONTENT_PROVIDER);
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        if (declaration.isAbstract()) {
            return;
        }

        String className = declaration.getQualifiedName();
        if (className == null) {
            return;
        }

        Map<String, Object> map = context.getPartialResult(ISSUE).getMap();
        map.put(className, context.createLocationHandle(declaration));
    }

    @Override
    public void checkPartialResults(Context context, PartialResult partialResult) {
        Map<String, Object> map = partialResult.getMap();
        if (map == null || map.isEmpty()) {
            return;
        }

        Project project = context.getMainProject();
        List<File> manifests = project.getManifestFiles();
        if (manifests == null || manifests.isEmpty()) {
            return;
        }

        Set<String> registered = new HashSet<>();
        for (File manifest : manifests) {
            registered.addAll(collectComponentNames(manifest));
        }

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String className = entry.getKey();
            if (!registered.contains(className)) {
                Location.Handle handle = (Location.Handle) entry.getValue();
                String message =
                        "The class "
                                + className
                                + " is not registered in the AndroidManifest.xml file; it must be declared via an <activity>, <service> or <provider> tag";
                context.report(ISSUE, handle, message);
            }
        }
    }

    private Set<String> collectComponentNames(File manifest) {
        Set<String> names = new HashSet<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(manifest);
            Element root = document.getDocumentElement();
            if (root == null) {
                return names;
            }

            String packageName = root.getAttribute("package");
            NodeList allElements = root.getElementsByTagName("*");
            for (int i = 0, n = allElements.getLength(); i < n; i++) {
                Node node = allElements.item(i);
                if (node.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element element = (Element) node;
                String tag = element.getTagName();
                if ("activity".equals(tag) || "service".equals(tag) || "provider".equals(tag)) {
                    String componentName = element.getAttribute("android:name");
                    if (componentName == null || componentName.isEmpty()) {
                        componentName = element.getAttribute("name");
                    }
                    if (componentName != null && !componentName.isEmpty()) {
                        names.add(resolveName(packageName, componentName));
                    }
                }
            }
        } catch (Exception e) {
            // Ignore malformed manifest files.
        }
        return names;
    }

    private static String resolveName(String packageName, String name) {
        if (name.startsWith(".")) {
            return packageName + name;
        }
        if (name.indexOf('.') >= 0) {
            return name;
        }
        return packageName + "." + name;
    }
}
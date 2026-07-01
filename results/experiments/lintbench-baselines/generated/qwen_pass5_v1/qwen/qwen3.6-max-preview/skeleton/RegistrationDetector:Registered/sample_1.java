package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.PartialResult;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import java.io.File;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
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

    private final Map<String, Location> pendingChecks = new HashMap<>();

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.app.Activity",
                "android.app.Service",
                "android.content.ContentProvider"
        );
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (declaration.isAbstract() || !declaration.isPublic()) {
            return;
        }
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }
        // Skip test classes
        if (qualifiedName.contains(".test.") || qualifiedName.endsWith("Test")) {
            return;
        }
        pendingChecks.put(qualifiedName, context.getLocation(declaration));
    }

    @Override
    public void checkPartialResults(
            @NonNull Context context, @NonNull PartialResult partialResults) {
        if (pendingChecks.isEmpty()) {
            return;
        }

        File manifest = context.getMainProject().getManifest();
        if (manifest == null || !manifest.exists()) {
            pendingChecks.clear();
            return;
        }

        String packageName = context.getMainProject().getPackageName();
        Set<String> registered = parseManifest(manifest, packageName);

        for (Map.Entry<String, Location> entry : pendingChecks.entrySet()) {
            if (!registered.contains(entry.getKey())) {
                context.report(ISSUE, entry.getValue(),
                        "Class is not registered in the manifest");
            }
        }
        pendingChecks.clear();
    }

    private Set<String> parseManifest(File manifest, String packageName) {
        Set<String> registered = new HashSet<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(manifest);

            String[] tags = {"activity", "service", "provider"};
            for (String tag : tags) {
                NodeList nodes = doc.getElementsByTagName(tag);
                for (int i = 0; i < nodes.getLength(); i++) {
                    Element el = (Element) nodes.item(i);
                    String name = el.getAttribute("android:name");
                    if (name != null && !name.isEmpty()) {
                        registered.add(resolveClassName(name, packageName));
                    }
                }
            }
        } catch (Exception ignored) {
            // Ignore manifest parsing errors
        }
        return registered;
    }

    private String resolveClassName(String name, String packageName) {
        if (packageName == null) {
            packageName = "";
        }
        if (name.startsWith(".")) {
            return packageName + name;
        } else if (!name.contains(".")) {
            return packageName.isEmpty() ? name : packageName + "." + name;
        }
        return name;
    }
}
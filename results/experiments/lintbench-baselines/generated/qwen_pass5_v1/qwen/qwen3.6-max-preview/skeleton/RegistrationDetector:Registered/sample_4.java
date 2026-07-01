package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.PartialResult;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.UastScanner;
import org.jetbrains.uast.UClass;

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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class RegistrationDetector extends Detector implements UastScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(RegistrationDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "Registered",
                    "Class is not registered in the manifest",
                    "Activities, services and content providers should be registered in the AndroidManifest.xml file using <activity>, <service> and <provider> tags.\n\n" +
                    "If your activity is simply a parent class intended to be subclassed by other \"real\" activities, make it an abstract class.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private final Map<String, Location> mPendingClasses = new HashMap<>();

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
        if (declaration.isAbstract()) {
            return;
        }
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName != null) {
            mPendingClasses.put(qualifiedName, context.getLocation(declaration));
        }
    }

    @Override
    public void checkPartialResults(
            @NonNull Context context, @NonNull PartialResult partialResults) {
        if (mPendingClasses.isEmpty()) {
            return;
        }

        File manifest = context.getMainProject().getManifest();
        String pkg = context.getMainProject().getPackage();
        if (manifest == null || !manifest.exists() || pkg == null) {
            return;
        }

        Set<String> registered = getRegisteredComponents(manifest, pkg);

        for (Map.Entry<String, Location> entry : mPendingClasses.entrySet()) {
            if (!registered.contains(entry.getKey())) {
                context.report(ISSUE, entry.getValue(),
                        "Class is not registered in the manifest");
            }
        }
        mPendingClasses.clear();
    }

    private Set<String> getRegisteredComponents(@NonNull File manifest, @NonNull String pkg) {
        Set<String> components = new HashSet<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(manifest);
            Element root = doc.getDocumentElement();
            NodeList apps = root.getElementsByTagName("application");
            if (apps.getLength() > 0) {
                Element app = (Element) apps.item(0);
                String[] tags = {"activity", "service", "provider"};
                String androidNs = "http://schemas.android.com/apk/res/android";
                for (String tag : tags) {
                    NodeList nodes = app.getElementsByTagName(tag);
                    for (int i = 0; i < nodes.getLength(); i++) {
                        Element el = (Element) nodes.item(i);
                        String name = el.getAttributeNS(androidNs, "name");
                        if (name != null && !name.isEmpty()) {
                            components.add(normalizeClassName(name, pkg));
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Manifest parsing failures should not crash the lint check
        }
        return components;
    }

    private String normalizeClassName(@NonNull String name, @NonNull String pkg) {
        if (name.startsWith(".")) {
            return pkg + name;
        } else if (name.indexOf('.') == -1) {
            return pkg + "." + name;
        }
        return name;
    }
}
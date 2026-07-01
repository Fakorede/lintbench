package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Detector.SourceCodeScanner;
import com.android.tools.lint.detector.api.Detector.XmlScanner;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidAutoDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST));

    public static final Issue ISSUE =
            Issue.create(
                    "MissingMediaBrowserServiceIntentFilter",
                    "Missing MediaBrowserService intent-filter",
                    "An Automotive Media App requires an exported service that extends "
                            + "`android.service.media.MediaBrowserService` with an `intent-filter` for the "
                            + "action `android.media.browse.MediaBrowserService` to be able to browse and play media.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private final Map<String, ServiceInfo> manifestServices = new HashMap<>();
    private final Map<String, Location> detectedClasses = new HashMap<>();

    private static class ServiceInfo {
        final String className;
        final Location location;
        final boolean hasIntentFilter;

        ServiceInfo(String className, Location location, boolean hasIntentFilter) {
            this.className = className;
            this.location = location;
            this.hasIntentFilter = hasIntentFilter;
        }
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return false;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("service");
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        manifestServices.clear();
        detectedClasses.clear();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if ("service".equals(element.getTagName())) {
            String className = getFullyQualifiedName(context, element);
            if (!className.isEmpty()) {
                boolean hasFilter = hasMediaBrowserServiceIntentFilter(element);
                Location location = context.getNameLocation(element);
                manifestServices.put(normalizeClassName(className), new ServiceInfo(className, location, hasFilter));
            }
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.service.media.MediaBrowserService",
                "androidx.media.MediaBrowserServiceCompat"
        );
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName != null) {
            String normalized = normalizeClassName(qualifiedName);
            Location location = context.getNameLocation(declaration);
            detectedClasses.put(normalized, location);
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, Location> entry : detectedClasses.entrySet()) {
            String className = entry.getKey();
            Location classLocation = entry.getValue();
            ServiceInfo serviceInfo = manifestServices.get(className);
            if (serviceInfo == null) {
                context.report(
                        ISSUE,
                        classLocation,
                        "Service extending MediaBrowserService is not declared in the manifest");
            } else if (!serviceInfo.hasIntentFilter) {
                context.report(
                        ISSUE,
                        serviceInfo.location,
                        "Missing intent-filter for action android.media.browse.MediaBrowserService in manifest");
            }
        }
    }

    private String getFullyQualifiedName(XmlContext context, Element serviceElement) {
        String name = serviceElement.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
        if (name.isEmpty()) {
            return "";
        }
        if (name.startsWith(".")) {
            String pkg = serviceElement.getOwnerDocument().getDocumentElement().getAttribute("package");
            if (pkg.isEmpty() && context.getProject() != null) {
                pkg = context.getProject().getPackage();
            }
            return pkg + name;
        } else if (!name.contains(".")) {
            String pkg = serviceElement.getOwnerDocument().getDocumentElement().getAttribute("package");
            if (pkg.isEmpty() && context.getProject() != null) {
                pkg = context.getProject().getPackage();
            }
            return pkg + "." + name;
        }
        return name;
    }

    private String normalizeClassName(String className) {
        if (className == null) {
            return "";
        }
        return className.replace('$', '.');
    }

    private boolean hasMediaBrowserServiceIntentFilter(Element serviceElement) {
        for (Element intentFilter : getChildrenByTagName(serviceElement, "intent-filter")) {
            for (Element action : getChildrenByTagName(intentFilter, "action")) {
                String name = action.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
                if ("android.media.browse.MediaBrowserService".equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<Element> getChildrenByTagName(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && name.equals(child.getNodeName())) {
                result.add((Element) child);
            }
        }
        return result;
    }
}
package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidAutoDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST));

    public static final Issue ISSUE =
            Issue.create(
                    "MissingMediaBrowserServiceIntentFilter",
                    "Missing MediaBrowserService intent-filter",
                    "An Automotive Media App requires an exported service that extends "
                            + "`android.service.media.MediaBrowserService` with an `intent-filter` "
                            + "for the action `android.media.browse.MediaBrowserService` to be "
                            + "able to browse and play media.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private final java.util.Set<String> mediaBrowserServiceClasses = new java.util.HashSet<>();
    private final java.util.List<ManifestService> manifestServices = new java.util.ArrayList<>();

    private static class ManifestService {
        final String className;
        final Location location;
        final boolean hasFilter;

        ManifestService(String className, Location location, boolean hasFilter) {
            this.className = className;
            this.location = location;
            this.hasFilter = hasFilter;
        }
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return false;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return java.util.Collections.singletonList("service");
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mediaBrowserServiceClasses.clear();
        manifestServices.clear();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
        if (name.isEmpty()) {
            return;
        }
        String pkg = element.getOwnerDocument().getDocumentElement().getAttribute("package");
        String fullName = name;
        if (name.startsWith(".")) {
            fullName = pkg + name;
        } else if (!name.contains(".")) {
            fullName = pkg + "." + name;
        }

        boolean hasFilter = false;
        org.w3c.dom.NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child instanceof Element && "intent-filter".equals(child.getNodeName())) {
                Element intentFilter = (Element) child;
                org.w3c.dom.NodeList filterChildren = intentFilter.getChildNodes();
                for (int j = 0; j < filterChildren.getLength(); j++) {
                    org.w3c.dom.Node filterChild = filterChildren.item(j);
                    if (filterChild instanceof Element && "action".equals(filterChild.getNodeName())) {
                        Element action = (Element) filterChild;
                        String actionName = action.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
                        if ("android.media.browse.MediaBrowserService".equals(actionName)) {
                            hasFilter = true;
                            break;
                        }
                    }
                }
            }
            if (hasFilter) {
                break;
            }
        }

        String normalizedClassName = fullName.replace('$', '.');
        manifestServices.add(new ManifestService(normalizedClassName, context.getLocation(element), hasFilter));
    }

    @Override
    public List<String> applicableSuperClasses() {
        return java.util.Arrays.asList("android.service.media.MediaBrowserService");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName != null) {
            mediaBrowserServiceClasses.add(qualifiedName.replace('$', '.'));
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (ManifestService ms : manifestServices) {
            if (mediaBrowserServiceClasses.contains(ms.className) && !ms.hasFilter) {
                context.report(
                        ISSUE,
                        ms.location,
                        "Missing MediaBrowserService intent-filter");
            }
        }
    }
}
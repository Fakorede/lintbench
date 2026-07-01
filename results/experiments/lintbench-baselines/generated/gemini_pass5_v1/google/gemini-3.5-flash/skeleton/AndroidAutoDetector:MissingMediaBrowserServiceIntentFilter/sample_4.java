package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
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
            new Implementation(AndroidAutoDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "MissingMediaBrowserServiceIntentFilter",
                    "Missing MediaBrowserService intent-filter",
                    "An Automotive Media App requires an exported service that extends " +
                    "`android.service.media.MediaBrowserService` with an `intent-filter` " +
                    "for the action `android.media.browse.MediaBrowserService` to be able " +
                    "to browse and play media.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private final java.util.Set<String> mediaBrowserServiceClasses = new java.util.HashSet<>();
    private final java.util.Map<String, ServiceInfo> manifestServices = new java.util.HashMap<>();

    private static class ServiceInfo {
        Element element;
        XmlContext context;
        boolean hasIntentFilter;
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
        // No-op
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mediaBrowserServiceClasses.clear();
        manifestServices.clear();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if ("service".equals(element.getTagName())) {
            String serviceName = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if (serviceName == null || serviceName.isEmpty()) {
                return;
            }
            String pkg = context.getProject().getPackage();
            String fqName = serviceName;
            if (serviceName.startsWith(".")) {
                fqName = pkg != null ? pkg + serviceName : serviceName;
            } else if (!serviceName.contains(".")) {
                fqName = pkg != null ? pkg + "." + serviceName : serviceName;
            }

            boolean hasIntentFilter = false;
            org.w3c.dom.NodeList children = element.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                org.w3c.dom.Node child = children.item(i);
                if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE && "intent-filter".equals(child.getNodeName())) {
                    org.w3c.dom.NodeList filterChildren = child.getChildNodes();
                    for (int j = 0; j < filterChildren.getLength(); j++) {
                        org.w3c.dom.Node filterChild = filterChildren.item(j);
                        if (filterChild.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE && "action".equals(filterChild.getNodeName())) {
                            Element action = (Element) filterChild;
                            String actionName = action.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
                            if ("android.media.browse.MediaBrowserService".equals(actionName)) {
                                hasIntentFilter = true;
                                break;
                            }
                        }
                    }
                }
                if (hasIntentFilter) {
                    break;
                }
            }

            ServiceInfo info = new ServiceInfo();
            info.element = element;
            info.context = context;
            info.hasIntentFilter = hasIntentFilter;
            manifestServices.put(normalizeClassName(fqName), info);
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return java.util.Arrays.asList(
                "android.service.media.MediaBrowserService",
                "androidx.media.MediaBrowserServiceCompat"
        );
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        String fqName = declaration.getQualifiedName();
        if (fqName != null) {
            mediaBrowserServiceClasses.add(normalizeClassName(fqName));
        }
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        for (String fqName : mediaBrowserServiceClasses) {
            ServiceInfo info = manifestServices.get(fqName);
            if (info != null && !info.hasIntentFilter) {
                info.context.report(
                        ISSUE,
                        info.element,
                        info.context.getLocation(info.element),
                        "Missing MediaBrowserService intent-filter"
                );
            }
        }
    }

    private String normalizeClassName(String className) {
        if (className == null) return null;
        return className.replace('$', '.');
    }
}
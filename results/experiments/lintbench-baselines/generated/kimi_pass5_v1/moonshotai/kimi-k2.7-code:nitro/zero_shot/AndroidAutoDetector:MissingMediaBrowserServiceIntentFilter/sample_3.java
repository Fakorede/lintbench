package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.UastParser;
import com.android.tools.lint.detector.api.XmlContext;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AndroidAutoDetector extends Detector
        implements Detector.XmlScanner, Detector.UastScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String MEDIA_BROWSER_SERVICE_CLASS = "android.service.media.MediaBrowserService";
    private static final String BROWSER_SERVICE_ACTION = "android.media.browse.MediaBrowserService";
    private static final String TAG_SERVICE = "service";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_EXPORTED = "exported";

    public static final Issue ISSUE = Issue.create(
            "MissingMediaBrowserServiceIntentFilter",
            "Missing MediaBrowserService intent-filter",
            "An Automotive Media App requires an exported service that extends "
                    + "android.service.media.MediaBrowserService with an intent-filter for the action "
                    + "android.media.browse.MediaBrowserService to be able to browse and play media.",
            Category.USABILITY,
            6,
            Severity.WARNING,
            new Implementation(AndroidAutoDetector.class, Scope.MANIFEST_SCOPE, Scope.JAVA_FILE_SCOPE),
            "https://developer.android.com/training/auto/audio/index.html#config_manifest"
    );

    private final Set<String> mMediaBrowserServiceClasses = new HashSet<>();
    private final Map<String, ServiceInfo> mManifestServices = new HashMap<>();

    private static class ServiceInfo {
        final Location location;
        final boolean exported;
        final boolean hasMediaBrowserAction;

        ServiceInfo(Location location, boolean exported, boolean hasMediaBrowserAction) {
            this.location = location;
            this.exported = exported;
            this.hasMediaBrowserAction = hasMediaBrowserAction;
        }
    }

    @Override
    public void beforeCheckRootProject(@NotNull Context context) {
        mMediaBrowserServiceClasses.clear();
        mManifestServices.clear();
    }

    @Override
    public void afterCheckRootProject(@NotNull Context context) {
        for (String className : mMediaBrowserServiceClasses) {
            ServiceInfo info = mManifestServices.get(className);
            if (info == null) {
                continue;
            }
            if (info.exported && !info.hasMediaBrowserAction) {
                String message = "The service " + className
                        + " must have an <intent-filter> with the action "
                        + BROWSER_SERVICE_ACTION;
                context.report(ISSUE, info.location, message);
            }
        }
    }

    @Override
    @NotNull
    public List<String> getApplicableElements() {
        return Collections.singletonList(TAG_SERVICE);
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        if (!TAG_SERVICE.equals(element.getTagName())) {
            return;
        }

        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        String packageName = context.getProject().getPackage();
        String fqn = getFullyQualifiedClassName(packageName, name);

        boolean hasFilters = false;
        boolean hasAction = false;

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            if (!TAG_INTENT_FILTER.equals(childElement.getTagName())) {
                continue;
            }
            hasFilters = true;

            NodeList filterChildren = childElement.getChildNodes();
            for (int j = 0; j < filterChildren.getLength(); j++) {
                Node filterChild = filterChildren.item(j);
                if (filterChild.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element filterChildElement = (Element) filterChild;
                if (TAG_ACTION.equals(filterChildElement.getTagName())) {
                    String action = filterChildElement.getAttributeNS(ANDROID_URI, ATTR_NAME);
                    if (BROWSER_SERVICE_ACTION.equals(action)) {
                        hasAction = true;
                    }
                }
            }
        }

        String exportedValue = element.getAttributeNS(ANDROID_URI, ATTR_EXPORTED);
        boolean exported;
        if (exportedValue != null && !exportedValue.isEmpty()) {
            exported = Boolean.parseBoolean(exportedValue);
        } else {
            exported = hasFilters;
        }

        mManifestServices.put(fqn, new ServiceInfo(
                context.getLocation(element), exported, hasAction));
    }

    @Override
    @NotNull
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    @NotNull
    public UastParser.UElementHandler createUastHandler(@NotNull JavaContext context) {
        return new UastParser.UElementHandler() {
            @Override
            public void visitClass(@NotNull UClass node) {
                if (context.getEvaluator().extendsClass(node, MEDIA_BROWSER_SERVICE_CLASS, false)) {
                    String qualifiedName = node.getQualifiedName();
                    if (qualifiedName != null) {
                        mMediaBrowserServiceClasses.add(qualifiedName);
                    }
                }
            }
        };
    }

    private static String getFullyQualifiedClassName(String packageName, String className) {
        if (className.startsWith(".")) {
            return packageName + className;
        }
        if (className.indexOf('.') < 0) {
            return packageName + "." + className;
        }
        return className;
    }
}
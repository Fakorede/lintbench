package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.client.api.UElementHandler;
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AndroidAutoDetector extends Detector implements Detector.XmlScanner, SourceCodeScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_PACKAGE = "package";
    private static final String TAG_SERVICE = "service";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";
    private static final String MEDIA_BROWSER_SERVICE = "android.service.media.MediaBrowserService";
    private static final String ACTION_MEDIA_BROWSER_SERVICE = "android.media.browse.MediaBrowserService";
    private static final String MESSAGE =
            "This MediaBrowserService must include an intent-filter with action "
                    + ACTION_MEDIA_BROWSER_SERVICE;

    public static final Issue ISSUE = Issue.create(
            "MissingMediaBrowserServiceIntentFilter",
            "Missing MediaBrowserService intent-filter",
            "Automotive media apps must declare an exported service that extends "
                    + MEDIA_BROWSER_SERVICE
                    + " with an intent-filter for the action "
                    + ACTION_MEDIA_BROWSER_SERVICE + ".",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(AndroidAutoDetector.class, Scope.JAVA_FILE_SCOPE, Scope.MANIFEST_SCOPE)
    );

    private final Set<String> mMediaBrowserServices = new HashSet<>();
    private final Map<String, Element> mManifestServiceElements = new HashMap<>();
    private final Map<String, Location> mManifestServiceLocations = new HashMap<>();

    @Override
    public void beforeCheckEachProject(@NotNull Context context) {
        mMediaBrowserServices.clear();
        mManifestServiceElements.clear();
        mManifestServiceLocations.clear();
    }

    @Override
    public void afterCheckEachProject(@NotNull Context context) {
        for (String fqcn : mMediaBrowserServices) {
            Element service = mManifestServiceElements.get(fqcn);
            if (service != null && !hasMediaBrowserIntentFilter(service)) {
                Location location = mManifestServiceLocations.get(fqcn);
                if (location != null) {
                    context.report(ISSUE, location, MESSAGE);
                }
            }
        }
    }

    @Override
    @NotNull
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.<Class<? extends UElement>>singletonList(UClass.class);
    }

    @Override
    @NotNull
    public UElementHandler createUastHandler(@NotNull final JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NotNull UClass node) {
                if (context.getEvaluator().extendsClass(node, MEDIA_BROWSER_SERVICE, false)) {
                    String fqcn = node.getQualifiedName();
                    if (fqcn != null && !MEDIA_BROWSER_SERVICE.equals(fqcn)) {
                        mMediaBrowserServices.add(fqcn);
                    }
                }
            }
        };
    }

    @Override
    public boolean appliesTo(@NotNull ResourceFolderType folderType) {
        return false;
    }

    @Override
    @NotNull
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_SERVICE);
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        Element root = context.document.getDocumentElement();
        String packageName = root.getAttribute(ATTR_PACKAGE);
        String fqcn = resolveFullyQualifiedName(packageName, name);
        if (fqcn != null) {
            mManifestServiceElements.put(fqcn, element);
            mManifestServiceLocations.put(fqcn, context.getLocation(element));
        }
    }

    private boolean hasMediaBrowserIntentFilter(@NotNull Element service) {
        NodeList children = service.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && TAG_INTENT_FILTER.equals(((Element) child).getTagName())) {
                Element filter = (Element) child;
                NodeList actions = filter.getElementsByTagName(TAG_ACTION);
                for (int j = 0; j < actions.getLength(); j++) {
                    Element action = (Element) actions.item(j);
                    String actionName = action.getAttributeNS(ANDROID_URI, ATTR_NAME);
                    if (ACTION_MEDIA_BROWSER_SERVICE.equals(actionName)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @NotNull
    private static String resolveFullyQualifiedName(@NotNull String packageName, @NotNull String name) {
        if (name.startsWith(".")) {
            return packageName + name;
        }
        if (name.contains(".")) {
            return name;
        }
        return packageName + "." + name;
    }
}
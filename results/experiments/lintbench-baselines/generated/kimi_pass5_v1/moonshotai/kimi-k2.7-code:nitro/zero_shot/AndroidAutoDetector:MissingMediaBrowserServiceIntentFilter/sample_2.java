package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
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

import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String MEDIA_BROWSER_SERVICE =
            "android.service.media.MediaBrowserService";
    private static final String MEDIA_BROWSER_SERVICE_ACTION =
            "android.media.browse.MediaBrowserService";
    private static final String SERVICE_TAG = "service";
    private static final String INTENT_FILTER_TAG = "intent-filter";
    private static final String ACTION_TAG = "action";

    private static final String MESSAGE =
            "Automotive Media App services that extend MediaBrowserService must include an "
                    + "intent-filter with action android.media.browse.MediaBrowserService";

    public static final Issue ISSUE = Issue.create(
            "MissingMediaBrowserServiceIntentFilter",
            "Missing MediaBrowserService intent-filter",
            "An Automotive Media App requires an exported service that extends "
                    + "android.service.media.MediaBrowserService with an intent-filter for the "
                    + "action android.media.browse.MediaBrowserService to be able to browse and "
                    + "play media. Add an intent-filter with the action to the service.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    AndroidAutoDetector.class,
                    EnumSet.of(Scope.MANIFEST_SCOPE, Scope.JAVA_FILE_SCOPE)
            )
    );

    private static final Set<String> sMediaBrowserServices = new HashSet<>();
    private static final List<ManifestServiceInfo> sManifestServices = new ArrayList<>();

    private static class ManifestServiceInfo {
        final XmlContext context;
        final Element element;
        final String className;
        final boolean hasMediaFilter;

        ManifestServiceInfo(XmlContext context, Element element, String className,
                boolean hasMediaFilter) {
            this.context = context;
            this.element = element;
            this.className = className;
            this.hasMediaFilter = hasMediaFilter;
        }
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        sMediaBrowserServices.clear();
        sManifestServices.clear();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (ManifestServiceInfo info : sManifestServices) {
            if (sMediaBrowserServices.contains(info.className) && !info.hasMediaFilter) {
                info.context.report(ISSUE, info.element,
                        info.context.getLocation(info.element), MESSAGE);
            }
        }
    }

    @Override
    @NonNull
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(MEDIA_BROWSER_SERVICE);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName != null) {
            sMediaBrowserServices.add(qualifiedName);
        }
    }

    @Override
    @NonNull
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SERVICE_TAG);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String packageName = null;
        if (context.getDocument() != null
                && context.getDocument().getDocumentElement() != null) {
            packageName = context.getDocument().getDocumentElement().getAttribute("package");
        }
        if (packageName == null || packageName.isEmpty()) {
            packageName = context.getMainProject().getPackage();
        }

        String name = element.getAttributeNS(SdkConstants.ANDROID_URI, "name");
        if (name == null || name.isEmpty()) {
            return;
        }

        String className;
        if (name.startsWith(".")) {
            className = packageName + name;
        } else if (name.indexOf('.') == -1) {
            className = packageName + "." + name;
        } else {
            className = name;
        }

        boolean hasAnyFilter = false;
        boolean hasMediaFilter = false;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE
                    || !INTENT_FILTER_TAG.equals(child.getNodeName())) {
                continue;
            }
            hasAnyFilter = true;

            NodeList actions = child.getChildNodes();
            for (int j = 0; j < actions.getLength(); j++) {
                Node action = actions.item(j);
                if (action.getNodeType() != Node.ELEMENT_NODE
                        || !ACTION_TAG.equals(action.getNodeName())) {
                    continue;
                }
                String actionName = ((Element) action)
                        .getAttributeNS(SdkConstants.ANDROID_URI, "name");
                if (MEDIA_BROWSER_SERVICE_ACTION.equals(actionName)) {
                    hasMediaFilter = true;
                    break;
                }
            }
            if (hasMediaFilter) {
                break;
            }
        }

        String exportedAttr = element.getAttributeNS(SdkConstants.ANDROID_URI, "exported");
        boolean isExported;
        if (!exportedAttr.isEmpty()) {
            isExported = Boolean.parseBoolean(exportedAttr);
        } else {
            isExported = hasAnyFilter;
        }

        if (isExported) {
            sManifestServices.add(new ManifestServiceInfo(context, element, className,
                    hasMediaFilter));
        }
    }
}
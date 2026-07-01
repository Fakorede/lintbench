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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String MEDIA_BROWSER_SERVICE_CLASS =
            "android.service.media.MediaBrowserService";
    private static final String MEDIA_BROWSER_SERVICE_ACTION =
            "android.media.browse.MediaBrowserService";

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    AndroidAutoDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "MissingMediaBrowserServiceIntentFilter",
                    "Missing MediaBrowserService intent-filter",
                    "An Automotive Media App must expose a service that extends "
                            + "android.service.media.MediaBrowserService with an intent-filter "
                            + "for android.media.browse.MediaBrowserService so that Android Auto "
                            + "can browse and play media.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private final Set<String> mMediaBrowserServices = new HashSet<>();
    private final List<ServiceInfo> mDeclaredServices = new ArrayList<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.XML;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("service");
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mMediaBrowserServices.clear();
        mDeclaredServices.clear();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (ServiceInfo info : mDeclaredServices) {
            if (mMediaBrowserServices.contains(info.resolvedClassName) && !info.hasMediaBrowserAction) {
                context.report(
                        ISSUE,
                        info.location,
                        "This MediaBrowserService is missing the required intent-filter action "
                                + "\"android.media.browse.MediaBrowserService\".");
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(ANDROID_URI, "name");
        if (name == null || name.isEmpty()) {
            return;
        }

        ServiceInfo info = new ServiceInfo();
        info.resolvedClassName = resolveServiceClassName(context, name);
        info.location = context.getLocation(element);
        info.hasMediaBrowserAction = hasMediaBrowserServiceAction(element);
        mDeclaredServices.add(info);
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(MEDIA_BROWSER_SERVICE_CLASS);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName != null) {
            mMediaBrowserServices.add(qualifiedName.replace('$', '.'));
        }
    }

    private static boolean hasMediaBrowserServiceAction(@NonNull Element service) {
        NodeList children = service.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && "intent-filter".equals(child.getLocalName())) {
                Element filter = (Element) child;
                NodeList actions = filter.getElementsByTagName("action");
                for (int j = 0; j < actions.getLength(); j++) {
                    Element action = (Element) actions.item(j);
                    String actionName = action.getAttributeNS(ANDROID_URI, "name");
                    if (MEDIA_BROWSER_SERVICE_ACTION.equals(actionName)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @NonNull
    private static String resolveServiceClassName(@NonNull XmlContext context, @NonNull String name) {
        String normalized = name.replace('$', '.');
        if (normalized.startsWith(".")) {
            String packageName = context.getMainProject().getPackage();
            return packageName + normalized;
        } else if (normalized.contains(".")) {
            return normalized;
        } else {
            String packageName = context.getMainProject().getPackage();
            return packageName + "." + normalized;
        }
    }

    private static class ServiceInfo {
        String resolvedClassName;
        Location location;
        boolean hasMediaBrowserAction;
    }
}
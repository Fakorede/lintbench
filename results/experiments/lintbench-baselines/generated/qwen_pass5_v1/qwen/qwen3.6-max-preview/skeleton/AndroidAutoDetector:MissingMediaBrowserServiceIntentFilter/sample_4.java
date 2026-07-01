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
import com.android.tools.lint.detector.api.XmlContext;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AndroidAutoDetector extends Detector implements Detector.SourceCodeScanner, Detector.XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String MEDIA_BROWSER_SERVICE_ACTION = "android.media.browse.MediaBrowserService";
    private static final String MEDIA_BROWSER_SERVICE_CLASS = "android.service.media.MediaBrowserService";

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidAutoDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "MissingMediaBrowserServiceIntentFilter",
                    "Missing MediaBrowserService intent-filter",
                    "An Automotive Media App requires an exported service that extends `android.service.media.MediaBrowserService` " +
                    "with an `intent-filter` for the action `android.media.browse.MediaBrowserService` to be able to browse and play media.\n\n" +
                    "To do this, add\n" +
                    "```xml\n" +
                    "`<intent-filter>`\n" +
                    "    `<action android:name=\"android.media.browse.MediaBrowserService\" />`\n" +
                    "`</intent-filter>`\n" +
                    "```\n" +
                    "to the service that extends `android.service.media.MediaBrowserService`",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private final Set<String> mediaBrowserServiceClasses = new HashSet<>();
    private final List<ServiceEntry> manifestServices = new ArrayList<>();

    private static class ServiceEntry {
        final XmlContext context;
        final Element element;
        final String className;
        ServiceEntry(XmlContext context, Element element, String className) {
            this.context = context;
            this.element = element;
            this.className = className;
        }
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MANIFEST;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("service");
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mediaBrowserServiceClasses.clear();
        manifestServices.clear();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!context.isManifestFile()) {
            return;
        }
        String name = element.getAttributeNS(ANDROID_URI, "name");
        if (name == null || name.isEmpty()) {
            return;
        }
        String pkg = context.getPackageName();
        String fqn = resolveClassName(name, pkg != null ? pkg : "");
        manifestServices.add(new ServiceEntry(context, element, fqn));
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(MEDIA_BROWSER_SERVICE_CLASS);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        String fqn = declaration.getQualifiedName();
        if (fqn != null) {
            mediaBrowserServiceClasses.add(fqn);
        }
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        for (ServiceEntry entry : manifestServices) {
            if (mediaBrowserServiceClasses.contains(entry.className)) {
                if (!hasMediaBrowserServiceIntentFilter(entry.element)) {
                    entry.context.report(ISSUE, entry.element, entry.context.getLocation(entry.element),
                            "Missing MediaBrowserService intent-filter");
                }
            }
        }
    }

    private static String resolveClassName(String name, String pkg) {
        if (name.startsWith(".")) {
            return pkg + name;
        } else if (name.indexOf('.') == -1) {
            return pkg + "." + name;
        }
        return name;
    }

    private static boolean hasMediaBrowserServiceIntentFilter(Element serviceElement) {
        NodeList children = serviceElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childEl = (Element) child;
                if ("intent-filter".equals(childEl.getTagName())) {
                    if (hasAction(childEl)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean hasAction(Element intentFilter) {
        NodeList children = intentFilter.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childEl = (Element) child;
                if ("action".equals(childEl.getTagName())) {
                    String name = childEl.getAttributeNS(ANDROID_URI, "name");
                    if (MEDIA_BROWSER_SERVICE_ACTION.equals(name)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
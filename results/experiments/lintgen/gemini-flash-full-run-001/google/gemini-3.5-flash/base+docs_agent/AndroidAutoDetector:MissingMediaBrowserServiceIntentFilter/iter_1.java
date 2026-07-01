package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
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
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PACKAGE;
import static com.android.SdkConstants.TAG_SERVICE;

public class AndroidAutoDetector extends Detector implements XmlScanner, SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
        "MissingMediaBrowserServiceIntentFilter",
        "Missing MediaBrowserService intent-filter",
        "An Automotive Media App requires an exported service that extends " +
        "`android.service.media.MediaBrowserService` with an `intent-filter` " +
        "for the action `android.media.browse.MediaBrowserService` to be able " +
        "to browse and play media.\n\n" +
        "To do this, add\n" +
        "```xml\n" +
        "<intent-filter>\n" +
        "    <action android:name=\"android.media.browse.MediaBrowserService\" />\n" +
        "</intent-filter>\n" +
        "```\n" +
        "to the service that extends `android.service.media.MediaBrowserService`.",
        Category.CORRECTNESS,
        6,
        Severity.ERROR,
        new Implementation(
            AndroidAutoDetector.class,
            EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)
        )
    );

    private final Map<String, ServiceInfo> services = new HashMap<>();

    private static class ServiceInfo {
        final String className;
        final boolean isExported;
        final boolean hasMediaBrowserIntentFilter;
        final Location location;

        ServiceInfo(String className, boolean isExported, boolean hasMediaBrowserIntentFilter, Location location) {
            this.className = className;
            this.isExported = isExported;
            this.hasMediaBrowserIntentFilter = hasMediaBrowserIntentFilter;
            this.location = location;
        }
    }

    @Override
    public void beforeCheckRootProject(com.android.tools.lint.detector.api.Context context) {
        services.clear();
    }

    // XmlScanner implementation

    @Override
    public List<String> getApplicableElements() {
        return Collections.singletonList(TAG_SERVICE);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }
        String pkg = element.getOwnerDocument().getDocumentElement().getAttribute(ATTR_PACKAGE);
        if (pkg == null || pkg.isEmpty()) {
            pkg = context.getProject().getPackage();
        }
        String fqName = name;
        if (name.startsWith(".")) {
            fqName = pkg + name;
        } else if (!name.contains(".")) {
            fqName = pkg + "." + name;
        }
        String normalizedFqName = fqName.replace('$', '.');

        boolean hasMediaBrowserIntentFilter = false;
        boolean hasAnyIntentFilter = false;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "intent-filter".equals(child.getNodeName())) {
                hasAnyIntentFilter = true;
                Element intentFilter = (Element) child;
                NodeList filterChildren = intentFilter.getChildNodes();
                for (int j = 0; j < filterChildren.getLength(); j++) {
                    Node filterChild = filterChildren.item(j);
                    if (filterChild.getNodeType() == Node.ELEMENT_NODE && "action".equals(filterChild.getNodeName())) {
                        Element action = (Element) filterChild;
                        String actionName = action.getAttributeNS(ANDROID_URI, ATTR_NAME);
                        if ("android.media.browse.MediaBrowserService".equals(actionName)) {
                            hasMediaBrowserIntentFilter = true;
                        }
                    }
                }
            }
        }

        String exportedAttr = element.getAttributeNS(ANDROID_URI, "exported");
        boolean isExported;
        if ("true".equals(exportedAttr)) {
            isExported = true;
        } else if ("false".equals(exportedAttr)) {
            isExported = false;
        } else {
            isExported = hasAnyIntentFilter;
        }

        Location location = context.getNameLocation(element);
        services.put(normalizedFqName, new ServiceInfo(normalizedFqName, isExported, hasMediaBrowserIntentFilter, location));
    }

    // SourceCodeScanner implementation

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.service.media.MediaBrowserService");
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        if (context.getEvaluator().isAbstract(declaration)) {
            return;
        }
        String fqName = declaration.getQualifiedName();
        if (fqName == null) {
            return;
        }
        String normalizedFqName = fqName.replace('$', '.');
        ServiceInfo info = services.get(normalizedFqName);

        if (info == null) {
            context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "Service extending `MediaBrowserService` must be declared in the manifest with an intent-filter for `android.media.browse.MediaBrowserService`."
            );
        } else if (!info.hasMediaBrowserIntentFilter) {
            context.report(
                ISSUE,
                info.location,
                "Service extending `MediaBrowserService` is missing the `android.media.browse.MediaBrowserService` intent-filter."
            );
        } else if (!info.isExported) {
            context.report(
                ISSUE,
                info.location,
                "Service extending `MediaBrowserService` must be exported."
            );
        }
    }
}
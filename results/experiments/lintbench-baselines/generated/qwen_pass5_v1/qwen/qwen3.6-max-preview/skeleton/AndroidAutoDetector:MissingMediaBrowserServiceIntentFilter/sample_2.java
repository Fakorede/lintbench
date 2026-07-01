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
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidAutoDetector extends Detector implements Detector.SourceCodeScanner, Detector.XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidAutoDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST));

    public static final Issue ISSUE =
            Issue.create(
                    "MissingMediaBrowserServiceIntentFilter",
                    "Missing MediaBrowserService intent-filter",
                    "An Automotive Media App requires an exported service that extends " +
                    "`android.service.media.MediaBrowserService` with an `intent-filter` for the action " +
                    "`android.media.browse.MediaBrowserService` to be able to browse and play media.\n\n" +
                    "To do this, add\n" +
                    "`<intent-filter>`\n" +
                    "    `<action android:name=\"android.media.browse.MediaBrowserService\" />`\n" +
                    "`</intent-filter>`\n" +
                    "to the service that extends `android.service.media.MediaBrowserService`",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String MEDIA_BROWSER_SERVICE_CLASS = "android.service.media.MediaBrowserService";
    private static final String MEDIA_BROWSER_SERVICE_ACTION = "android.media.browse.MediaBrowserService";

    private final Set<String> mMediaBrowserServices = new HashSet<>();

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
        mMediaBrowserServices.clear();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(ANDROID_URI, "name");
        if (name == null || name.isEmpty()) {
            return;
        }

        String pkg = context.getProject().getPackageName();
        String fqn = resolveClassName(name, pkg);

        if (!mMediaBrowserServices.contains(fqn)) {
            return;
        }

        boolean hasFilter = hasMediaBrowserIntentFilter(element);
        String exportedAttr = element.getAttributeNS(ANDROID_URI, "exported");
        boolean isExported = "true".equalsIgnoreCase(exportedAttr);

        if (!hasFilter || !isExported) {
            context.report(ISSUE, element, context.getLocation(element),
                    "MediaBrowserService must be exported and declare an intent-filter for " + MEDIA_BROWSER_SERVICE_ACTION);
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(MEDIA_BROWSER_SERVICE_CLASS);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        String fqn = declaration.getQualifiedName();
        if (fqn != null) {
            mMediaBrowserServices.add(fqn);
        }
    }

    public void visitMethod(@NonNull JavaContext context, @NonNull UMethod node) {
        // Not applicable for this detector
    }

    private static String resolveClassName(String name, String pkg) {
        if (pkg == null) pkg = "";
        if (name.startsWith(".")) {
            return pkg + name;
        } else if (name.indexOf('.') == -1) {
            return pkg + "." + name;
        }
        return name;
    }

    private static boolean hasMediaBrowserIntentFilter(Element service) {
        NodeList children = service.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "intent-filter".equals(child.getNodeName())) {
                Element filter = (Element) child;
                NodeList filterChildren = filter.getChildNodes();
                for (int j = 0; j < filterChildren.getLength(); j++) {
                    Node actionNode = filterChildren.item(j);
                    if (actionNode.getNodeType() == Node.ELEMENT_NODE && "action".equals(actionNode.getNodeName())) {
                        Element action = (Element) actionNode;
                        if (MEDIA_BROWSER_SERVICE_ACTION.equals(action.getAttributeNS(ANDROID_URI, "name"))) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
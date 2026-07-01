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
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

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
                    "<intent-filter>\n" +
                    "    <action android:name=\"android.media.browse.MediaBrowserService\" />\n" +
                    "</intent-filter>\n" +
                    "```\n" +
                    "to the service that extends `android.service.media.MediaBrowserService`",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private Set<String> mMediaBrowserServices = new HashSet<>();

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
        mMediaBrowserServices.clear();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(ANDROID_URI, "name");
        if (name == null || name.isEmpty()) {
            return;
        }

        String pkg = context.getManifestPackage();
        if (pkg == null) {
            return;
        }

        String fqn;
        if (name.startsWith(".")) {
            fqn = pkg + name;
        } else if (name.indexOf('.') == -1) {
            fqn = pkg + "." + name;
        } else {
            fqn = name;
        }

        if (!mMediaBrowserServices.contains(fqn)) {
            return;
        }

        boolean hasIntentFilter = false;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "intent-filter".equals(child.getNodeName())) {
                Element filter = (Element) child;
                NodeList actions = filter.getElementsByTagName("action");
                for (int j = 0; j < actions.getLength(); j++) {
                    Element action = (Element) actions.item(j);
                    if (MEDIA_BROWSER_SERVICE_ACTION.equals(action.getAttributeNS(ANDROID_URI, "name"))) {
                        hasIntentFilter = true;
                        break;
                    }
                }
            }
            if (hasIntentFilter) break;
        }

        if (!hasIntentFilter) {
            context.report(ISSUE, element, context.getLocation(element),
                    "MediaBrowserService must have an intent-filter with action " + MEDIA_BROWSER_SERVICE_ACTION);
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(MEDIA_BROWSER_SERVICE_CLASS);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        String qn = declaration.getQualifiedName();
        if (qn != null) {
            mMediaBrowserServices.add(qn);
        }
    }
}
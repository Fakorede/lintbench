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
import org.jetbrains.uast.UMethod;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AndroidAutoDetector extends Detector implements Detector.SourceCodeScanner, Detector.XmlScanner {

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

    private static final String MEDIA_BROWSER_SERVICE_ACTION = "android.media.browse.MediaBrowserService";
    private static final String MEDIA_BROWSER_SERVICE_CLASS = "android.service.media.MediaBrowserService";

    private Set<String> mediaBrowserServiceClasses;

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
        mediaBrowserServiceClasses = new HashSet<>();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttribute("android:name");
        if (name.isEmpty()) {
            return;
        }

        String packageName = context.getProject().getPackageName();
        String fqn = name;
        if (name.startsWith(".")) {
            fqn = packageName + name;
        } else if (!name.contains(".")) {
            fqn = packageName + "." + name;
        }

        if (!mediaBrowserServiceClasses.contains(fqn)) {
            return;
        }

        boolean hasIntentFilter = false;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "intent-filter".equals(child.getNodeName())) {
                Element intentFilter = (Element) child;
                NodeList actions = intentFilter.getElementsByTagName("action");
                for (int j = 0; j < actions.getLength(); j++) {
                    Element action = (Element) actions.item(j);
                    if (MEDIA_BROWSER_SERVICE_ACTION.equals(action.getAttribute("android:name"))) {
                        hasIntentFilter = true;
                        break;
                    }
                }
                if (hasIntentFilter) break;
            }
        }

        if (!hasIntentFilter) {
            context.report(ISSUE, element, context.getLocation(element),
                    "MediaBrowserService must have an intent-filter with action " + MEDIA_BROWSER_SERVICE_ACTION);
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(MEDIA_BROWSER_SERVICE_CLASS);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (!declaration.isInterface() && !declaration.isAbstract()) {
            String fqn = context.getEvaluator().getQualifiedName(declaration);
            if (fqn != null) {
                mediaBrowserServiceClasses.add(fqn);
            }
        }
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UMethod method) {
        // Not used for this detector
    }
}
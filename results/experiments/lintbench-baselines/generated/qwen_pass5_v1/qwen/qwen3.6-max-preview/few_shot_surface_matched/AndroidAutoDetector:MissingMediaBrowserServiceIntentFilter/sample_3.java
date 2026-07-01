package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
import org.jetbrains.uast.UMethod;
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

    private static final String MEDIA_BROWSER_SERVICE_CLASS = "android.service.media.MediaBrowserService";
    private static final String MEDIA_BROWSER_SERVICE_ACTION = "android.media.browse.MediaBrowserService";

    public static final Issue ISSUE = Issue.create(
            "MissingMediaBrowserServiceIntentFilter",
            "Missing MediaBrowserService intent-filter",
            "An Automotive Media App requires an exported service that extends "
                    + "`android.service.media.MediaBrowserService` with an `intent-filter` for the action "
                    + "`android.media.browse.MediaBrowserService` to be able to browse and play media.\n\n"
                    + "To do this, add\n"
                    + "`<intent-filter>`\n"
                    + "    `<action android:name=\"android.media.browse.MediaBrowserService\" />`\n"
                    + "`</intent-filter>`\n"
                    + "to the service that extends `android.service.media.MediaBrowserService`",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(AndroidAutoDetector.class, EnumSet.of(Scope.JAVA_FILE_SCOPE, Scope.MANIFEST_SCOPE)));

    private Set<String> mediaBrowserServiceClasses = new HashSet<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return false;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mediaBrowserServiceClasses.clear();
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("service");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String nameAttr = element.getAttribute("android:name");
        if (nameAttr.isEmpty()) {
            return;
        }

        String pkg = context.getMainProject().getPackage();
        String fqn = nameAttr;
        if (pkg != null) {
            if (nameAttr.startsWith(".")) {
                fqn = pkg + nameAttr;
            } else if (nameAttr.indexOf('.') == -1) {
                fqn = pkg + "." + nameAttr;
            }
        }

        boolean isMediaBrowserService = mediaBrowserServiceClasses.contains(fqn)
                || mediaBrowserServiceClasses.stream().anyMatch(c -> c.endsWith("." + nameAttr));

        if (!isMediaBrowserService) {
            return;
        }

        boolean hasCorrectIntentFilter = false;
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "intent-filter".equals(child.getNodeName())) {
                Element intentFilter = (Element) child;
                NodeList actions = intentFilter.getElementsByTagName("action");
                for (int j = 0; j < actions.getLength(); j++) {
                    Element action = (Element) actions.item(j);
                    if (MEDIA_BROWSER_SERVICE_ACTION.equals(action.getAttribute("android:name"))) {
                        hasCorrectIntentFilter = true;
                        break;
                    }
                }
            }
            if (hasCorrectIntentFilter) {
                break;
            }
        }

        if (!hasCorrectIntentFilter) {
            context.report(ISSUE, element, context.getLocation(element),
                    "MediaBrowserService implementation must have an intent-filter with action "
                            + "`android.media.browse.MediaBrowserService`");
        }
    }

    @Nullable
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
    public void visitMethod(@NonNull JavaContext context, @NonNull UMethod method) {
        // Required override per specification. No method-level analysis needed for this issue.
    }
}
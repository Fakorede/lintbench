package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String MEDIA_BROWSER_SERVICE_CLASS = "android.service.media.MediaBrowserService";
    private static final String MEDIA_BROWSER_SERVICE_ACTION = "android.media.browse.MediaBrowserService";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private Set<String> mediaBrowserServiceClasses;

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
            5,
            Severity.ERROR,
            new Implementation(AndroidAutoDetector.class, Scope.JAVA_FILE_SCOPE, Scope.MANIFEST_SCOPE));

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return true;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mediaBrowserServiceClasses = new HashSet<>();
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(MEDIA_BROWSER_SERVICE_CLASS);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass node) {
        String qualifiedName = node.getQualifiedName();
        if (qualifiedName != null) {
            mediaBrowserServiceClasses.add(qualifiedName);
        }
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UMethod node) {
        // No method-level checks required for this issue
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("service");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String serviceName = element.getAttributeNS(ANDROID_URI, "name");
        if (serviceName == null || serviceName.isEmpty()) {
            return;
        }

        String pkg = context.getProject().getPackageName();
        String fqn = serviceName;
        if (pkg != null) {
            if (fqn.startsWith(".")) {
                fqn = pkg + fqn;
            } else if (!fqn.contains(".")) {
                fqn = pkg + "." + fqn;
            }
        }

        if (!mediaBrowserServiceClasses.contains(fqn)) {
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
                    String actionName = action.getAttributeNS(ANDROID_URI, "name");
                    if (MEDIA_BROWSER_SERVICE_ACTION.equals(actionName)) {
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
                    "MediaBrowserService must have an intent-filter with action " + MEDIA_BROWSER_SERVICE_ACTION);
        }
    }
}
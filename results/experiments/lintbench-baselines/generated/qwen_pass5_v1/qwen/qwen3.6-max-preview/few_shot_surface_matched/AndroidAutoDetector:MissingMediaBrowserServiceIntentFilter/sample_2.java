package com.android.tools.lint.checks;

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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String MEDIA_BROWSER_SERVICE = "android.service.media.MediaBrowserService";
    private static final String MEDIA_BROWSER_SERVICE_COMPAT = "android.support.v4.media.MediaBrowserServiceCompat";
    private static final String ANDROIDX_MEDIA_BROWSER_SERVICE_COMPAT = "androidx.media.MediaBrowserServiceCompat";
    private static final String ACTION_MEDIA_BROWSER_SERVICE = "android.media.browse.MediaBrowserService";
    private static final String TAG_SERVICE = "service";
    private static final String ATTR_NAME = "android:name";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";

    public static final Issue ISSUE = Issue.create(
            "MissingMediaBrowserServiceIntentFilter",
            "Missing MediaBrowserService intent-filter",
            "An Automotive Media App requires an exported service that extends "
                    + "`android.service.media.MediaBrowserService` with an `intent-filter` for the "
                    + "action `android.media.browse.MediaBrowserService` to be able to browse and play media.\n\n"
                    + "To do this, add\n"
                    + "`<intent-filter>`\n"
                    + "    `<action android:name=\"android.media.browse.MediaBrowserService\" />`\n"
                    + "`</intent-filter>`\n"
                    + "to the service that extends `android.service.media.MediaBrowserService`",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(AndroidAutoDetector.class, Scope.JAVA_FILE_SCOPE, Scope.MANIFEST_SCOPE));

    private Set<String> mediaBrowserServices;

    @Override
    public boolean appliesTo(Context context, File file) {
        return true;
    }

    @Override
    public void beforeCheckRootProject(Context context) {
        mediaBrowserServices = new HashSet<>();
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(MEDIA_BROWSER_SERVICE, MEDIA_BROWSER_SERVICE_COMPAT, ANDROIDX_MEDIA_BROWSER_SERVICE_COMPAT);
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        String fqn = declaration.getQualifiedName();
        if (fqn != null) {
            mediaBrowserServices.add(fqn);
        }
    }

    @Override
    public void visitMethod(JavaContext context, UMethod method) {
        // Not required for this detector logic
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_SERVICE);
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String name = element.getAttribute(ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        String packageName = context.getPackageName();
        String fqn = name;
        if (name.startsWith(".")) {
            fqn = packageName + name;
        } else if (!name.contains(".")) {
            fqn = packageName + "." + name;
        }

        if (!mediaBrowserServices.contains(fqn)) {
            return;
        }

        NodeList children = element.getChildNodes();
        boolean hasCorrectFilter = false;
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && TAG_INTENT_FILTER.equals(child.getNodeName())) {
                Element filter = (Element) child;
                NodeList actions = filter.getElementsByTagName(TAG_ACTION);
                for (int j = 0; j < actions.getLength(); j++) {
                    Element action = (Element) actions.item(j);
                    if (ACTION_MEDIA_BROWSER_SERVICE.equals(action.getAttribute(ATTR_NAME))) {
                        hasCorrectFilter = true;
                        break;
                    }
                }
            }
            if (hasCorrectFilter) break;
        }

        if (!hasCorrectFilter) {
            context.report(ISSUE, element, context.getLocation(element),
                    "MediaBrowserService must have an intent-filter with action " + ACTION_MEDIA_BROWSER_SERVICE);
        }
    }
}
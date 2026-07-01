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
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ATTR_NAME = "name";
    private static final String TAG_SERVICE = "service";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";
    private static final String MEDIA_BROWSER_SERVICE_ACTION = "android.media.browse.MediaBrowserService";
    private static final String MEDIA_BROWSER_SERVICE_CLASS = "android.service.media.MediaBrowserService";

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidAutoDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST));

    public static final Issue ISSUE =
            Issue.create(
                    "MissingMediaBrowserServiceIntentFilter",
                    "Missing MediaBrowserService intent-filter",
                    "An Automotive Media App requires an exported service that extends "
                            + "`android.service.media.MediaBrowserService` with an `intent-filter` "
                            + "for the action `android.media.browse.MediaBrowserService` to be able to "
                            + "browse and play media.\n\n"
                            + "To do this, add\n"
                            + "```xml\n"
                            + "<intent-filter>\n"
                            + "    <action android:name=\"android.media.browse.MediaBrowserService\" />\n"
                            + "</intent-filter>\n"
                            + "```\n"
                            + "to the service that extends `android.service.media.MediaBrowserService`",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private final Map<String, Location> mMediaBrowserServices = new HashMap<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MANIFEST;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_SERVICE);
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mMediaBrowserServices.clear();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (name.isEmpty()) {
            return;
        }

        String fqn = resolveClassName(context, name);
        if (!mMediaBrowserServices.containsKey(fqn)) {
            return;
        }

        if (hasMediaBrowserIntentFilter(element)) {
            mMediaBrowserServices.remove(fqn);
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(MEDIA_BROWSER_SERVICE_CLASS);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        String fqn = context.getEvaluator().getQualifiedName(declaration);
        if (fqn != null) {
            mMediaBrowserServices.put(fqn, context.getLocation(declaration));
        }
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UMethod method) {
        // Not used for this detector
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, Location> entry : mMediaBrowserServices.entrySet()) {
            context.report(ISSUE, entry.getValue(),
                    "Service `%1$s` extends `MediaBrowserService` but does not have the required "
                            + "intent-filter for `android.media.browse.MediaBrowserService`",
                    entry.getKey());
        }
    }

    @NonNull
    private static String resolveClassName(@NonNull XmlContext context, @NonNull String name) {
        if (name.startsWith(".")) {
            return context.getPackageName() + name;
        } else if (name.indexOf('.') == -1) {
            return context.getPackageName() + "." + name;
        }
        return name;
    }

    private static boolean hasMediaBrowserIntentFilter(@NonNull Element serviceElement) {
        NodeList children = serviceElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childEl = (Element) child;
                if (TAG_INTENT_FILTER.equals(childEl.getTagName())) {
                    if (hasAction(childEl, MEDIA_BROWSER_SERVICE_ACTION)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean hasAction(@NonNull Element intentFilter, @NonNull String actionName) {
        NodeList children = intentFilter.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childEl = (Element) child;
                if (TAG_ACTION.equals(childEl.getTagName())) {
                    String name = childEl.getAttributeNS(ANDROID_URI, ATTR_NAME);
                    if (actionName.equals(name)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
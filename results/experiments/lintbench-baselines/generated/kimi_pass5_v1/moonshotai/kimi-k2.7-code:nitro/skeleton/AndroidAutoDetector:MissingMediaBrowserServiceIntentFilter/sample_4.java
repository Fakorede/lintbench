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
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidAutoDetector extends Detector implements Detector.SourceCodeScanner, Detector.XmlScanner {

    private static final String MEDIA_BROWSER_SERVICE_CLASS = "android.service.media.MediaBrowserService";
    private static final String MEDIA_BROWSER_SERVICE_ACTION = "android.media.browse.MediaBrowserService";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidAutoDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST));

    public static final Issue ISSUE =
            Issue.create(
                    "MissingMediaBrowserServiceIntentFilter",
                    "Missing MediaBrowserService intent-filter",
                    "An Automotive Media App must declare an exported service that extends "
                            + "android.service.media.MediaBrowserService with an intent-filter for the action "
                            + "android.media.browse.MediaBrowserService in the manifest.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private Set<String> mMediaBrowserServices;

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
        mMediaBrowserServices = new HashSet<>();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String serviceName = element.getAttributeNS(ANDROID_URI, "name");
        if (serviceName == null || serviceName.isEmpty()) {
            serviceName = element.getAttribute("android:name");
        }
        if (serviceName == null || serviceName.isEmpty()) {
            return;
        }

        String resolvedName = resolveServiceClassName(element, serviceName);
        if (resolvedName == null || !mMediaBrowserServices.contains(resolvedName)) {
            return;
        }

        if (!hasMediaBrowserServiceIntentFilter(element)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Missing MediaBrowserService intent-filter. Add an <intent-filter> with "
                            + "<action android:name=\"android.media.browse.MediaBrowserService\" />.");
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(MEDIA_BROWSER_SERVICE_CLASS);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName != null) {
            mMediaBrowserServices.add(qualifiedName);
        }
    }

    private static String resolveServiceClassName(@NonNull Element serviceElement, @NonNull String name) {
        Element manifest = serviceElement.getOwnerDocument().getDocumentElement();
        if (manifest == null) {
            return null;
        }
        String packageName = manifest.getAttribute("package");
        if (packageName == null) {
            packageName = "";
        }

        if (name.startsWith(".")) {
            return packageName + name;
        } else if (!name.contains(".")) {
            return packageName + "." + name;
        }
        return name;
    }

    private static boolean hasMediaBrowserServiceIntentFilter(@NonNull Element serviceElement) {
        NodeList intentFilters = serviceElement.getElementsByTagName("intent-filter");
        for (int i = 0; i < intentFilters.getLength(); i++) {
            Node filterNode = intentFilters.item(i);
            if (!(filterNode instanceof Element)) {
                continue;
            }
            Element intentFilter = (Element) filterNode;
            NodeList actions = intentFilter.getElementsByTagName("action");
            for (int j = 0; j < actions.getLength(); j++) {
                Node actionNode = actions.item(j);
                if (!(actionNode instanceof Element)) {
                    continue;
                }
                Element action = (Element) actionNode;
                String actionName = action.getAttributeNS(ANDROID_URI, "name");
                if (actionName == null || actionName.isEmpty()) {
                    actionName = action.getAttribute("android:name");
                }
                if (MEDIA_BROWSER_SERVICE_ACTION.equals(actionName)) {
                    return true;
                }
            }
        }
        return false;
    }
}
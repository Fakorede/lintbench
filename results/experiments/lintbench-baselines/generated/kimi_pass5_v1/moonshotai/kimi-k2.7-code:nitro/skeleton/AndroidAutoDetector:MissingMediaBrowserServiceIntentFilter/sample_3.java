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
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String MEDIA_BROWSER_SERVICE_CLASS =
            "android.service.media.MediaBrowserService";
    private static final String MEDIA_BROWSER_SERVICE_ACTION =
            "android.media.browse.MediaBrowserService";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final String TAG_SERVICE = "service";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";
    private static final String ATTR_NAME = "name";

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    AndroidAutoDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "MissingMediaBrowserServiceIntentFilter",
                    "Missing MediaBrowserService intent-filter",
                    "An Automotive media app must expose a service that extends "
                            + "android.service.media.MediaBrowserService. That service must be "
                            + "exported and declare an <intent-filter> containing the action "
                            + "android.media.browse.MediaBrowserService so that Android Auto can "
                            + "browse and play media.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private Map<String, ClassInfo> mMediaBrowserServices;
    private Set<String> mValidServices;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return true;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_SERVICE);
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mMediaBrowserServices = new HashMap<>();
        mValidServices = new HashSet<>();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        String fqcn = getFqcn(context.getMainProject().getPackage(), name);
        if (fqcn == null) {
            return;
        }

        if (hasMediaBrowserAction(element)) {
            mValidServices.add(fqcn);
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
            mMediaBrowserServices.put(qualifiedName, new ClassInfo(declaration, context));
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Map.Entry<String, ClassInfo> entry : mMediaBrowserServices.entrySet()) {
            if (!mValidServices.contains(entry.getKey())) {
                ClassInfo info = entry.getValue();
                info.context.report(
                        ISSUE,
                        info.declaration,
                        info.context.getNameLocation(info.declaration),
                        "Add an <intent-filter> with action android.media.browse.MediaBrowserService "
                                + "to this MediaBrowserService and ensure it is exported.");
            }
        }
    }

    private static boolean hasMediaBrowserAction(Element service) {
        NodeList children = service.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            if (!TAG_INTENT_FILTER.equals(child.getNodeName())) {
                continue;
            }

            Element filter = (Element) child;
            NodeList actions = filter.getChildNodes();
            for (int j = 0; j < actions.getLength(); j++) {
                Node actionNode = actions.item(j);
                if (actionNode.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                if (!TAG_ACTION.equals(actionNode.getNodeName())) {
                    continue;
                }

                Element action = (Element) actionNode;
                String actionName = action.getAttributeNS(ANDROID_URI, ATTR_NAME);
                if (MEDIA_BROWSER_SERVICE_ACTION.equals(actionName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String getFqcn(String packageName, String className) {
        if (className == null || className.isEmpty()) {
            return null;
        }
        if (className.startsWith(".")) {
            return packageName + className;
        }
        if (className.contains(".")) {
            return className;
        }
        return packageName + "." + className;
    }

    private static final class ClassInfo {
        final UClass declaration;
        final JavaContext context;

        ClassInfo(UClass declaration, JavaContext context) {
            this.declaration = declaration;
            this.context = context;
        }
    }
}
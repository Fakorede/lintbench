package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String TAG_SERVICE = "service";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";
    private static final String ATTR_NAME = "name";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ACTION_MEDIA_BROWSER_SERVICE = "android.media.browse.MediaBrowserService";
    private static final String CLASS_MEDIA_BROWSER_SERVICE = "android.service.media.MediaBrowserService";

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    AndroidAutoDetector.class,
                    java.util.EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "MissingMediaBrowserServiceIntentFilter",
                    "Missing MediaBrowserService intent-filter",
                    "An Android Auto media app must expose a service that extends "
                            + "android.service.media.MediaBrowserService and declare an <intent-filter> "
                            + "with the action android.media.browse.MediaBrowserService in the manifest.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private java.util.Set<String> mMediaBrowserServices;

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return false;
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Collections.singletonList(TAG_SERVICE);
    }

    @Override
    public void beforeCheckRootProject(com.android.tools.lint.detector.api.Context context) {
        mMediaBrowserServices = new java.util.HashSet<>();
    }

    @Override
    public void visitElement(com.android.tools.lint.detector.api.XmlContext context,
                             org.w3c.dom.Element element) {
        if (!TAG_SERVICE.equals(element.getTagName())) {
            return;
        }

        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (name == null || name.isEmpty()) {
            return;
        }

        String packageName = context.getProject().getPackage();
        String fqcn = normalizeClassName(getFqcn(packageName, name));
        if (fqcn == null || !mMediaBrowserServices.contains(fqcn)) {
            return;
        }

        if (!hasMediaBrowserServiceIntentFilter(element)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "This service extends MediaBrowserService but is missing the required "
                            + "<intent-filter> with <action android:name=\""
                            + ACTION_MEDIA_BROWSER_SERVICE
                            + "\" />.");
        }
    }

    @Override
    public java.util.List<String> applicableSuperClasses() {
        return java.util.Collections.singletonList(CLASS_MEDIA_BROWSER_SERVICE);
    }

    @Override
    public void visitClass(com.android.tools.lint.detector.api.JavaContext context,
                           UClass declaration) {
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName != null) {
            mMediaBrowserServices.add(normalizeClassName(qualifiedName));
        }
    }

    @Override
    public void visitMethod(com.android.tools.lint.detector.api.JavaContext context,
                            UMethod method,
                            PsiMethod superMethod) {
        // Not needed for this check.
    }

    private static boolean hasMediaBrowserServiceIntentFilter(org.w3c.dom.Element service) {
        org.w3c.dom.NodeList children = service.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                continue;
            }
            org.w3c.dom.Element childElement = (org.w3c.dom.Element) child;
            if (!TAG_INTENT_FILTER.equals(childElement.getTagName())) {
                continue;
            }
            org.w3c.dom.NodeList actions = childElement.getElementsByTagName(TAG_ACTION);
            for (int j = 0; j < actions.getLength(); j++) {
                org.w3c.dom.Node actionNode = actions.item(j);
                if (actionNode.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
                    continue;
                }
                org.w3c.dom.Element action = (org.w3c.dom.Element) actionNode;
                String actionName = action.getAttributeNS(ANDROID_URI, ATTR_NAME);
                if (ACTION_MEDIA_BROWSER_SERVICE.equals(actionName)) {
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
            return (packageName != null && !packageName.isEmpty())
                    ? packageName + className
                    : className.substring(1);
        }
        if (packageName != null && !packageName.isEmpty() && className.indexOf('.') == -1) {
            return packageName + "." + className;
        }
        return className;
    }

    private static String normalizeClassName(String className) {
        if (className == null) {
            return null;
        }
        return className.replace('$', '.');
    }
}
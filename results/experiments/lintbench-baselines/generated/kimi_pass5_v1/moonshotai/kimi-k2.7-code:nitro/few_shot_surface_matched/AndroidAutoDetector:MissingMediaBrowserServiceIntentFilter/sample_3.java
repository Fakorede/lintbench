package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import com.intellij.psi.*;
import org.jetbrains.uast.*;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue MISSING_MEDIA_BROWSER_SERVICE_INTENT_FILTER =
            Issue.create(
                    "MissingMediaBrowserServiceIntentFilter",
                    "Missing MediaBrowserService intent filter",
                    "An Automotive Media App requires an exported service that extends "
                            + "`android.service.media.MediaBrowserService` with an `<intent-filter>`"
                            + " for the action `android.media.browse.MediaBrowserService` to be able"
                            + " to browse and play media. Add an `<intent-filter>` containing that"
                            + " `<action>` to the service that extends MediaBrowserService.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            AndroidAutoDetector.class,
                            java.util.EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)))
                    .setAndroidSpecific(true);

    private static final String MEDIA_BROWSER_SERVICE = "android.service.media.MediaBrowserService";
    private static final String ACTION_MEDIA_BROWSER_SERVICE =
            "android.media.browse.MediaBrowserService";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final String TAG_SERVICE = "service";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";
    private static final String ATTR_NAME = "name";

    private final java.util.Set<String> mMediaBrowserServices = new java.util.HashSet<>();
    private final java.util.Map<String, java.util.List<ServiceInfo>> mPendingServices =
            new java.util.HashMap<>();

    private static class ServiceInfo {
        final XmlContext context;
        final org.w3c.dom.Element element;

        ServiceInfo(XmlContext context, org.w3c.dom.Element element) {
            this.context = context;
            this.element = element;
        }
    }

    @Override
    public boolean appliesTo(JavaContext context, PsiElement node) {
        return true;
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Collections.singletonList(TAG_SERVICE);
    }

    @Override
    public void beforeCheckRootProject(Context context) {
        mMediaBrowserServices.clear();
        mPendingServices.clear();
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        if (!TAG_SERVICE.equals(element.getLocalName())
                && !TAG_SERVICE.equals(element.getTagName())) {
            return;
        }

        String rawName = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (rawName == null || rawName.isEmpty()) {
            rawName = element.getAttribute("android:" + ATTR_NAME);
        }
        if (rawName == null || rawName.isEmpty()) {
            return;
        }

        String packageName = context.getProject().getPackage();
        String className;
        if (packageName == null || packageName.isEmpty()) {
            className = rawName;
        } else {
            className = getQualifiedClassName(rawName, packageName);
        }

        if (mMediaBrowserServices.contains(className)) {
            checkService(context, element, className);
        } else {
            mPendingServices
                    .computeIfAbsent(className, k -> new java.util.ArrayList<>())
                    .add(new ServiceInfo(context, element));
        }
    }

    @Override
    public java.util.List<String> applicableSuperClasses() {
        return java.util.Collections.singletonList(MEDIA_BROWSER_SERVICE);
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        String name = declaration.getQualifiedName();
        if (name == null || MEDIA_BROWSER_SERVICE.equals(name)) {
            return;
        }
        mMediaBrowserServices.add(name);
        java.util.List<ServiceInfo> pending = mPendingServices.remove(name);
        if (pending != null) {
            for (ServiceInfo info : pending) {
                checkService(info.context, info.element, name);
            }
        }
    }

    public void visitMethod(JavaContext context, UMethod method) {
        // No source-code method checks are required for this issue.
    }

    private static String getQualifiedClassName(String rawName, String packageName) {
        if (rawName.startsWith(".")) {
            return packageName + rawName;
        }
        if (rawName.contains(".")) {
            return rawName;
        }
        return packageName + "." + rawName;
    }

    private static void checkService(
            XmlContext context, org.w3c.dom.Element service, String className) {
        boolean hasFilter = false;
        org.w3c.dom.NodeList filters = service.getElementsByTagName(TAG_INTENT_FILTER);
        for (int i = 0; i < filters.getLength(); i++) {
            org.w3c.dom.Element filter = (org.w3c.dom.Element) filters.item(i);
            org.w3c.dom.NodeList actions = filter.getElementsByTagName(TAG_ACTION);
            for (int j = 0; j < actions.getLength(); j++) {
                org.w3c.dom.Element action = (org.w3c.dom.Element) actions.item(j);
                String actionName = action.getAttributeNS(ANDROID_URI, ATTR_NAME);
                if (actionName == null || actionName.isEmpty()) {
                    actionName = action.getAttribute("android:" + ATTR_NAME);
                }
                if (ACTION_MEDIA_BROWSER_SERVICE.equals(actionName)) {
                    hasFilter = true;
                    break;
                }
            }
            if (hasFilter) {
                break;
            }
        }

        if (!hasFilter) {
            context.report(
                    MISSING_MEDIA_BROWSER_SERVICE_INTENT_FILTER,
                    service,
                    context.getLocation(service),
                    "The service `"
                            + className
                            + "` extends MediaBrowserService but is missing an `<intent-filter>`"
                            + " with action `"
                            + ACTION_MEDIA_BROWSER_SERVICE
                            + "`");
        }
    }
}
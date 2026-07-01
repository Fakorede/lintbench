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
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String ISSUE_ID = "MissingOnPlayFromSearch";
    private static final String ACTION_PLAY_FROM_SEARCH = "android.media.action.PLAY_FROM_SEARCH";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String TAG_ACTION = "action";
    private static final String ATTR_NAME = "name";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_SERVICE = "service";
    private static final String MEDIA_BROWSER_SERVICE = "android.service.media.MediaBrowserService";
    private static final String MEDIA_BROWSER_SERVICE_COMPAT = "android.support.v4.media.MediaBrowserServiceCompat";
    private static final String ANDROIDX_MEDIA_BROWSER_SERVICE_COMPAT = "androidx.media.MediaBrowserServiceCompat";
    private static final String METHOD_ON_PLAY_FROM_SEARCH = "onPlayFromSearch";

    public static final Issue ISSUE =
            Issue.create(
                    ISSUE_ID,
                    "Missing onPlayFromSearch implementation",
                    "To support voice searches on Android Auto, a service that handles the "
                            + "PLAY_FROM_SEARCH action must also override and implement "
                            + "`onPlayFromSearch(String query, Bundle extras)`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            AndroidAutoDetector.class,
                            java.util.EnumSet.of(Scope.MANIFEST_SCOPE, Scope.JAVA_FILE_SCOPE)));

    private java.util.Set<String> mPlayFromSearchServices;
    private java.util.Map<String, ServiceInfo> mMediaBrowserServices;

    private static class ServiceInfo {
        final JavaContext context;
        final UClass declaration;
        final boolean hasOnPlayFromSearch;

        ServiceInfo(JavaContext context, UClass declaration, boolean hasOnPlayFromSearch) {
            this.context = context;
            this.declaration = declaration;
            this.hasOnPlayFromSearch = hasOnPlayFromSearch;
        }
    }

    @Override
    public boolean appliesTo(Context context, java.io.File file) {
        return true;
    }

    @Override
    public java.util.Collection<String> getApplicableElements() {
        return java.util.Collections.singletonList(TAG_ACTION);
    }

    @Override
    public void beforeCheckRootProject(Context context) {
        mPlayFromSearchServices = new java.util.HashSet<>();
        mMediaBrowserServices = new java.util.HashMap<>();
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        if (!TAG_ACTION.equals(element.getTagName())) {
            return;
        }

        String actionName = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (!ACTION_PLAY_FROM_SEARCH.equals(actionName)) {
            return;
        }

        org.w3c.dom.Node parent = element.getParentNode();
        if (parent == null || !TAG_INTENT_FILTER.equals(parent.getNodeName())) {
            return;
        }

        org.w3c.dom.Node serviceNode = parent.getParentNode();
        if (serviceNode == null || !TAG_SERVICE.equals(serviceNode.getNodeName())) {
            return;
        }

        if (!(serviceNode instanceof org.w3c.dom.Element)) {
            return;
        }

        org.w3c.dom.Element service = (org.w3c.dom.Element) serviceNode;
        String className = service.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (className == null || className.isEmpty()) {
            return;
        }

        className = resolveClassName(context.getProject().getPackage(), className);
        if (className != null) {
            mPlayFromSearchServices.add(className);
        }
    }

    @Override
    public java.util.List<String> applicableSuperClasses() {
        return java.util.Arrays.asList(
                MEDIA_BROWSER_SERVICE,
                MEDIA_BROWSER_SERVICE_COMPAT,
                ANDROIDX_MEDIA_BROWSER_SERVICE_COMPAT);
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }

        boolean hasOverride = false;
        for (PsiMethod method : declaration.getMethods()) {
            if (METHOD_ON_PLAY_FROM_SEARCH.equals(method.getName())) {
                PsiParameter[] params = method.getParameterList().getParameters();
                if (params.length == 2
                        && params[0].getType().equalsToText("java.lang.String")
                        && params[1].getType().equalsToText("android.os.Bundle")) {
                    hasOverride = true;
                    break;
                }
            }
        }

        mMediaBrowserServices.put(
                qualifiedName, new ServiceInfo(context, declaration, hasOverride));
    }

    @Override
    public java.util.List<String> getApplicableMethodNames() {
        return java.util.Collections.singletonList(METHOD_ON_PLAY_FROM_SEARCH);
    }

    @Override
    public void visitMethod(JavaContext context, UCallExpression call, PsiMethod method) {
        // Override detection for onPlayFromSearch is performed in visitClass.
    }

    @Override
    public void afterCheckRootProject(Context context) {
        for (String serviceClass : mPlayFromSearchServices) {
            ServiceInfo info = mMediaBrowserServices.get(serviceClass);
            if (info != null && !info.hasOnPlayFromSearch) {
                info.context.report(
                        ISSUE,
                        info.declaration,
                        info.context.getNameLocation(info.declaration),
                        "This service must override onPlayFromSearch(String query, Bundle extras) to support voice searches on Android Auto");
            }
        }
    }

    private static String resolveClassName(String packageName, String className) {
        if (className == null || className.isEmpty()) {
            return null;
        }

        if (className.startsWith(".")) {
            if (packageName == null || packageName.isEmpty()) {
                return className.substring(1);
            }
            return packageName + className;
        }

        if (className.contains(".")) {
            return className;
        }

        if (packageName == null || packageName.isEmpty()) {
            return className;
        }

        return packageName + "." + className;
    }
}
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
import com.intellij.psi.PsiType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String ACTION_MEDIA_PLAY_FROM_SEARCH =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";
    private static final String ANDROID_URI =
            "http://schemas.android.com/apk/res/android";
    private static final String ATTR_NAME = "name";

    private static final String MEDIA_BROWSER_SERVICE =
            "android.service.media.MediaBrowserService";
    private static final String MEDIA_BROWSER_SERVICE_COMPAT =
            "android.support.v4.media.MediaBrowserServiceCompat";
    private static final String ANDROIDX_MEDIA_BROWSER_SERVICE_COMPAT =
            "androidx.media.MediaBrowserServiceCompat";

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    AndroidAutoDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST));

    public static final Issue ISSUE =
            Issue.create(
                    "MissingOnPlayFromSearch",
                    "Missing `onPlayFromSearch`",
                    "To support voice searches on Android Auto, your `MediaBrowserService` "
                            + "must declare an intent filter for the action "
                            + "`android.media.action.MEDIA_PLAY_FROM_SEARCH` and also "
                            + "override and implement `onPlayFromSearch(String query, Bundle bundle)`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private final List<ServiceInfo> mVoiceSearchServices = new ArrayList<>();
    private final Map<String, Boolean> mMediaBrowserServices = new HashMap<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return false;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("service");
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                MEDIA_BROWSER_SERVICE,
                MEDIA_BROWSER_SERVICE_COMPAT,
                ANDROIDX_MEDIA_BROWSER_SERVICE_COMPAT);
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mVoiceSearchServices.clear();
        mMediaBrowserServices.clear();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!"service".equals(element.getTagName())) {
            return;
        }

        String className = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (className == null || className.isEmpty()) {
            return;
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE
                    || !"intent-filter".equals(child.getNodeName())) {
                continue;
            }
            Element intentFilter = (Element) child;
            NodeList actions = intentFilter.getElementsByTagName("action");
            for (int j = 0; j < actions.getLength(); j++) {
                Element action = (Element) actions.item(j);
                String actionName = action.getAttributeNS(ANDROID_URI, ATTR_NAME);
                if (ACTION_MEDIA_PLAY_FROM_SEARCH.equals(actionName)) {
                    String packageName = context.getMainProject().getPackage();
                    String qualifiedName = getQualifiedClassName(packageName, className);
                    if (qualifiedName != null) {
                        mVoiceSearchServices.add(
                                new ServiceInfo(qualifiedName, context.getLocation(element)));
                    }
                    return;
                }
            }
        }
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }

        boolean hasOnPlayFromSearch = hasOnPlayFromSearch(declaration);
        mMediaBrowserServices.put(qualifiedName, hasOnPlayFromSearch);
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (ServiceInfo info : mVoiceSearchServices) {
            Boolean hasMethod = mMediaBrowserServices.get(info.className);
            if (hasMethod != null && !hasMethod) {
                context.report(
                        ISSUE,
                        info.location,
                        "To support voice searches on Android Auto, override and implement "
                                + "`onPlayFromSearch(String query, Bundle bundle)` in this service.");
            }
        }
    }

    private static boolean hasOnPlayFromSearch(@NonNull UClass cls) {
        for (UMethod method : cls.getMethods()) {
            if (!"onPlayFromSearch".equals(method.getName())) {
                continue;
            }
            List<UParameter> parameters = method.getUastParameters();
            if (parameters.size() != 2) {
                continue;
            }
            PsiType first = parameters.get(0).getType();
            PsiType second = parameters.get(1).getType();
            if (first == null || second == null) {
                continue;
            }
            if ("java.lang.String".equals(first.getCanonicalText())
                    && "android.os.Bundle".equals(second.getCanonicalText())) {
                return true;
            }
        }
        return false;
    }

    private static String getQualifiedClassName(String packageName, String className) {
        if (className == null || className.isEmpty()) {
            return null;
        }
        if (className.startsWith(".")) {
            return packageName + className;
        } else if (className.contains(".")) {
            return className;
        } else {
            return packageName + "." + className;
        }
    }

    private static class ServiceInfo {
        final String className;
        final Location location;

        ServiceInfo(String className, Location location) {
            this.className = className;
            this.location = location;
        }
    }
}
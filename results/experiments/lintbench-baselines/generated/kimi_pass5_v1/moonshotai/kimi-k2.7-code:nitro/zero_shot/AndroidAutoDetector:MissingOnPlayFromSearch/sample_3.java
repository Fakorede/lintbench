package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SuppressWarnings("UnstableApiUsage")
public class AndroidAutoDetector extends Detector implements XmlScanner, SourceCodeScanner {

    private static final String MEDIA_BROWSER_SERVICE =
            "android.media.browse.MediaBrowserService";
    private static final String MEDIA_BROWSER_SERVICE_COMPAT =
            "android.support.v4.media.MediaBrowserServiceCompat";
    private static final String MEDIA_BROWSER_SERVICE_ACTION =
            "android.media.browse.MediaBrowserService";
    private static final String ON_PLAY_FROM_SEARCH = "onPlayFromSearch";
    private static final String TYPE_STRING = "java.lang.String";
    private static final String TYPE_BUNDLE = "android.os.Bundle";

    private final Set<String> mMediaBrowserServices = new HashSet<>();

    public static final Issue MISSING_ON_PLAY_FROM_SEARCH = Issue.create(
            "MissingOnPlayFromSearch",
            "Missing onPlayFromSearch",
            "To support voice searches on Android Auto, in addition to adding an "
                    + "intent-filter for the action android.media.browse.MediaBrowserService, "
                    + "you also need to override and implement "
                    + "onPlayFromSearch(String query, Bundle bundle).",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    AndroidAutoDetector.class,
                    Scope.MANIFEST_SCOPE,
                    Scope.JAVA_FILE_SCOPE));

    @Override
    @NonNull
    public List<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_SERVICE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!SdkConstants.TAG_SERVICE.equals(element.getTagName())) {
            return;
        }

        String serviceName = element.getAttributeNS(
                SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
        if (serviceName == null || serviceName.isEmpty()) {
            return;
        }

        String packageName = context.getMainProject().getPackage();
        serviceName = resolveFullClassName(packageName, serviceName);

        NodeList intentFilters = element.getElementsByTagName(SdkConstants.TAG_INTENT_FILTER);
        for (int i = 0; i < intentFilters.getLength(); i++) {
            Element intentFilter = (Element) intentFilters.item(i);
            NodeList actions = intentFilter.getElementsByTagName(SdkConstants.TAG_ACTION);
            for (int j = 0; j < actions.getLength(); j++) {
                Element action = (Element) actions.item(j);
                String actionName = action.getAttributeNS(
                        SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                if (MEDIA_BROWSER_SERVICE_ACTION.equals(actionName)) {
                    mMediaBrowserServices.add(serviceName);
                    return;
                }
            }
        }
    }

    @Override
    @Nullable
    public List<String> applicableSuperClasses() {
        return Arrays.asList(MEDIA_BROWSER_SERVICE, MEDIA_BROWSER_SERVICE_COMPAT);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull PsiClass node) {
        String className = node.getQualifiedName();
        if (className == null || !mMediaBrowserServices.contains(className)) {
            return;
        }

        boolean hasOnPlayFromSearch = false;
        for (PsiMethod method : node.findMethodsByName(ON_PLAY_FROM_SEARCH, false)) {
            PsiParameter[] params = method.getParameterList().getParameters();
            if (params.length == 2
                    && TYPE_STRING.equals(params[0].getType().getCanonicalText())
                    && TYPE_BUNDLE.equals(params[1].getType().getCanonicalText())) {
                hasOnPlayFromSearch = true;
                break;
            }
        }

        if (!hasOnPlayFromSearch) {
            context.report(
                    MISSING_ON_PLAY_FROM_SEARCH,
                    context.getNameLocation(node),
                    "Override and implement onPlayFromSearch(String, Bundle) "
                            + "to support voice searches on Android Auto.");
        }
    }

    private static String resolveFullClassName(@Nullable String packageName,
            @NonNull String className) {
        if (className.startsWith(".")) {
            return packageName != null ? packageName + className : className;
        }
        if (packageName != null && !className.contains(".")) {
            return packageName + "." + className;
        }
        return className;
    }
}
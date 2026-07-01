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
import com.android.tools.lint.detector.api.UElementHandler;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AndroidAutoDetector extends Detector implements Detector.XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingOnPlayFromSearch",
            "Missing onPlayFromSearch override",
            "To support voice searches on Android Auto, you must override and implement "
                    + "`onPlayFromSearch(String, Bundle)` in your MediaBrowserService.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(AndroidAutoDetector.class, EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE))
    );

    private static final String MEDIA_BROWSER_SERVICE = "android.media.browse.MediaBrowserService";
    private static final String MEDIA_BROWSER_SERVICE_COMPAT = "android.support.v4.media.MediaBrowserServiceCompat";
    private static final String ANDROIDX_MEDIA_BROWSER_SERVICE_COMPAT = "androidx.media.MediaBrowserServiceCompat";
    private static final String ACTION_MEDIA_BROWSER_SERVICE = "android.media.browse.MediaBrowserService";
    private static final String ON_PLAY_FROM_SEARCH = "onPlayFromSearch";
    private static final String FQCN_STRING = "java.lang.String";
    private static final String FQCN_BUNDLE = "android.os.Bundle";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String TAG_SERVICE = "service";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";
    private static final String ATTR_NAME = "name";

    private final Map<String, Element> mManifestMediaBrowserServices = new HashMap<>();
    private final Map<String, UClass> mServiceClasses = new HashMap<>();
    private final Map<String, JavaContext> mServiceClassContexts = new HashMap<>();
    private final Set<String> mReported = new HashSet<>();

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        mManifestMediaBrowserServices.clear();
        mServiceClasses.clear();
        mServiceClassContexts.clear();
        mReported.clear();
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        for (Map.Entry<String, Element> entry : mManifestMediaBrowserServices.entrySet()) {
            String serviceClass = entry.getKey();
            UClass cls = mServiceClasses.get(serviceClass);
            JavaContext javaContext = mServiceClassContexts.get(serviceClass);
            if (cls != null && javaContext != null && !mReported.contains(serviceClass)
                    && !hasOnPlayFromSearch(javaContext, cls)) {
                reportMissing(javaContext, cls);
            }
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_SERVICE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        NodeList intentFilters = element.getElementsByTagName(TAG_INTENT_FILTER);
        for (int i = 0; i < intentFilters.getLength(); i++) {
            Element filter = (Element) intentFilters.item(i);
            NodeList actions = filter.getElementsByTagName(TAG_ACTION);
            for (int j = 0; j < actions.getLength(); j++) {
                Element action = (Element) actions.item(j);
                String actionName = action.getAttributeNS(ANDROID_URI, ATTR_NAME);
                if (ACTION_MEDIA_BROWSER_SERVICE.equals(actionName)) {
                    String serviceName = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
                    if (serviceName != null && !serviceName.isEmpty()) {
                        String fqcn = resolveServiceName(context, serviceName);
                        mManifestMediaBrowserServices.put(fqcn, element);
                    }
                }
            }
        }
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return true;
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUElementTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    @NonNull
    public UElementHandler createUElementHandler(@NonNull final JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NonNull UClass node) {
                PsiClass psiClass = node.getPsi();
                if (psiClass == null) {
                    return;
                }

                String qualifiedName = node.getQualifiedName();
                if (qualifiedName == null) {
                    return;
                }

                mServiceClasses.put(qualifiedName, node);
                mServiceClassContexts.put(qualifiedName, context);

                if (mManifestMediaBrowserServices.containsKey(qualifiedName)
                        && !mReported.contains(qualifiedName)
                        && !hasOnPlayFromSearch(context, node)) {
                    reportMissing(context, node);
                }
            }
        };
    }

    private void reportMissing(@NonNull JavaContext context, @NonNull UClass declaration) {
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName == null || !mReported.add(qualifiedName)) {
            return;
        }
        context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "You must override `onPlayFromSearch(String, Bundle)` to support voice searches on Android Auto"
        );
    }

    private static boolean hasOnPlayFromSearch(@NonNull JavaContext context, @NonNull UClass declaration) {
        for (PsiMethod method : declaration.getMethods()) {
            if (!ON_PLAY_FROM_SEARCH.equals(method.getName())) {
                continue;
            }
            if (!PsiType.VOID.equals(method.getReturnType())) {
                continue;
            }
            PsiParameter[] parameters = method.getParameterList().getParameters();
            if (parameters.length != 2) {
                continue;
            }
            PsiClass first = context.getEvaluator().getTypeClass(parameters[0].getType());
            PsiClass second = context.getEvaluator().getTypeClass(parameters[1].getType());
            if (first != null && FQCN_STRING.equals(first.getQualifiedName())
                    && second != null && FQCN_BUNDLE.equals(second.getQualifiedName())) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private static String resolveServiceName(@NonNull XmlContext context, @NonNull String name) {
        String pkg = context.getMainProject().getPackage();
        if (pkg == null) {
            return name;
        }
        if (name.startsWith(".")) {
            return pkg + name;
        }
        if (name.indexOf('.') == -1) {
            return pkg + "." + name;
        }
        return name;
    }
}
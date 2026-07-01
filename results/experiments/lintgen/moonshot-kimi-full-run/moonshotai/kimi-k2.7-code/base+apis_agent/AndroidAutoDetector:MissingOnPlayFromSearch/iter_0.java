package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.SdkConstants;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AndroidAutoDetector extends Detector implements Detector.XmlScanner, Detector.SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingOnPlayFromSearch",
            "Missing onPlayFromSearch override",
            "To support voice searches on Android Auto, you must override and implement "
                    + "`onPlayFromSearch(String, Bundle)` in your MediaBrowserService.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(AndroidAutoDetector.class, Scope.MANIFEST, Scope.JAVA_FILE)
    );

    private static final String MEDIA_BROWSER_SERVICE = "android.media.browse.MediaBrowserService";
    private static final String MEDIA_BROWSER_SERVICE_COMPAT = "android.support.v4.media.MediaBrowserServiceCompat";
    private static final String ANDROIDX_MEDIA_BROWSER_SERVICE_COMPAT = "androidx.media.MediaBrowserServiceCompat";
    private static final String ACTION_MEDIA_BROWSER_SERVICE = "android.media.browse.MediaBrowserService";
    private static final String ON_PLAY_FROM_SEARCH = "onPlayFromSearch";
    private static final String FQCN_STRING = "java.lang.String";
    private static final String FQCN_BUNDLE = "android.os.Bundle";

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
        return Collections.singletonList(SdkConstants.TAG_SERVICE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        NodeList intentFilters = element.getElementsByTagName(SdkConstants.TAG_INTENT_FILTER);
        for (int i = 0; i < intentFilters.getLength(); i++) {
            Element filter = (Element) intentFilters.item(i);
            NodeList actions = filter.getElementsByTagName(SdkConstants.TAG_ACTION);
            for (int j = 0; j < actions.getLength(); j++) {
                Element action = (Element) actions.item(j);
                String actionName = action.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                if (ACTION_MEDIA_BROWSER_SERVICE.equals(actionName)) {
                    String serviceName = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
                    if (serviceName != null && !serviceName.isEmpty()) {
                        String fqcn = resolveServiceName(context, serviceName);
                        mManifestMediaBrowserServices.put(fqcn, element);
                    }
                }
            }
        }
    }

    @Override
    public List<Class<? extends org.jetbrains.uast.UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NotNull UClass node) {
                AndroidAutoDetector.this.visitClass(context, node);
            }
        };
    }

    private void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        PsiClass psiClass = declaration.getPsi();
        if (psiClass == null) {
            return;
        }
        if (!context.getEvaluator().extendsClass(psiClass, MEDIA_BROWSER_SERVICE, false)
                && !context.getEvaluator().extendsClass(psiClass, MEDIA_BROWSER_SERVICE_COMPAT, false)
                && !context.getEvaluator().extendsClass(psiClass, ANDROIDX_MEDIA_BROWSER_SERVICE_COMPAT, false)) {
            return;
        }

        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }

        mServiceClasses.put(qualifiedName, declaration);
        mServiceClassContexts.put(qualifiedName, context);

        if (mManifestMediaBrowserServices.containsKey(qualifiedName) && !hasOnPlayFromSearch(context, declaration)) {
            reportMissing(context, declaration);
        }
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
        for (UMethod method : declaration.getUastMethods()) {
            if (!ON_PLAY_FROM_SEARCH.equals(method.getName())) {
                continue;
            }
            if (!PsiType.VOID.equals(method.getReturnType())) {
                continue;
            }
            List<UParameter> params = method.getUastParameters();
            if (params.size() != 2) {
                continue;
            }
            PsiClass first = context.getEvaluator().getTypeClass(params.get(0).getType());
            PsiClass second = context.getEvaluator().getTypeClass(params.get(1).getType());
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
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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String MEDIA_BROWSER_SERVICE = "android.media.browse.MediaBrowserService";
    private static final String ON_PLAY_FROM_SEARCH = "onPlayFromSearch";
    private static final String STRING_TYPE = "java.lang.String";
    private static final String BUNDLE_TYPE = "android.os.Bundle";

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    AndroidAutoDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "MissingOnPlayFromSearch",
                    "Missing onPlayFromSearch",
                    "To support voice searches on Android Auto, a MediaBrowserService declared in "
                            + "the manifest with the android.media.browse.MediaBrowserService action "
                            + "must override and implement onPlayFromSearch(String, Bundle).",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private List<String> mMediaBrowserServices;

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
        mMediaBrowserServices = new ArrayList<>();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!"service".equals(element.getTagName())) {
            return;
        }

        String name = element.getAttributeNS(ANDROID_URI, "name");
        if (name == null || name.isEmpty()) {
            return;
        }

        NodeList filters = element.getElementsByTagName("intent-filter");
        for (int i = 0; i < filters.getLength(); i++) {
            Element filter = (Element) filters.item(i);
            NodeList actions = filter.getElementsByTagName("action");
            for (int j = 0; j < actions.getLength(); j++) {
                Element action = (Element) actions.item(j);
                String actionName = action.getAttributeNS(ANDROID_URI, "name");
                if (MEDIA_BROWSER_SERVICE.equals(actionName)) {
                    String qualified = getQualifiedClassName(context, name);
                    if (qualified != null) {
                        mMediaBrowserServices.add(qualified);
                    }
                    return;
                }
            }
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.media.browse.MediaBrowserService",
                "android.support.v4.media.MediaBrowserServiceCompat",
                "androidx.media.MediaBrowserServiceCompat");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        PsiClass psiClass = declaration.getJavaPsi();
        if (psiClass == null) {
            return;
        }

        String qualifiedName = psiClass.getQualifiedName();
        if (qualifiedName == null || !mMediaBrowserServices.contains(qualifiedName)) {
            return;
        }

        for (PsiMethod method : psiClass.getMethods()) {
            if (isOnPlayFromSearch(method)) {
                return;
            }
        }

        context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "MediaBrowserService must override onPlayFromSearch(String, Bundle) to support "
                        + "voice searches on Android Auto");
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return null;
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            org.jetbrains.uast.visitor.JavaElementVisitor visitor,
            @NonNull PsiMethod method,
            @NonNull UCallExpression call) {
        // Not used; detection is performed in visitClass.
    }

    private boolean isOnPlayFromSearch(@NonNull PsiMethod method) {
        if (!ON_PLAY_FROM_SEARCH.equals(method.getName())) {
            return false;
        }
        PsiParameter[] parameters = method.getParameterList().getParameters();
        if (parameters.length != 2) {
            return false;
        }
        return isType(parameters[0], STRING_TYPE) && isType(parameters[1], BUNDLE_TYPE);
    }

    private boolean isType(@NonNull PsiParameter parameter, @NonNull String expectedType) {
        PsiType type = parameter.getType();
        return type != null && expectedType.equals(type.getCanonicalText());
    }

    private String getQualifiedClassName(@NonNull XmlContext context, @NonNull String name) {
        if (name.startsWith(".")) {
            String packageName = context.getMainProject().getPackageName();
            return packageName != null ? packageName + name : null;
        }
        if (name.contains(".")) {
            return name;
        }
        String packageName = context.getMainProject().getPackageName();
        return packageName != null ? packageName + "." + name : null;
    }
}
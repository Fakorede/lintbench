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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
import org.w3c.dom.Element;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidAutoDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "MissingOnPlayFromSearch",
                    "Missing `onPlayFromSearch`",
                    "To support voice searches on Android Auto, in addition to adding an intent-filter for the action onPlayFromSearch, you also need to override and implement onPlayFromSearch(String query, Bundle bundle)",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private Set<String> mTargetClasses;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return true;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("action");
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mTargetClasses = new HashSet<>();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!"AndroidManifest.xml".equals(context.file.getName())) {
            return;
        }

        String actionName = element.getAttributeNS(ANDROID_URI, "name");
        if ("android.media.action.MEDIA_PLAY_FROM_SEARCH".equals(actionName) || "onPlayFromSearch".equals(actionName)) {
            Element parent = (Element) element.getParentNode();
            while (parent != null && !"activity".equals(parent.getTagName()) && !"service".equals(parent.getTagName())) {
                parent = (Element) parent.getParentNode();
            }

            if (parent != null) {
                String className = parent.getAttributeNS(ANDROID_URI, "name");
                if (className != null && !className.isEmpty()) {
                    String pkg = context.getMainProject().getPackage();
                    if (className.startsWith(".")) {
                        className = (pkg != null ? pkg : "") + className;
                    } else if (className.indexOf('.') == -1 && pkg != null) {
                        className = pkg + "." + className;
                    }
                    mTargetClasses.add(className);
                }
            }
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.media.session.MediaSession.Callback",
                "androidx.media.session.MediaSessionCompat.Callback",
                "android.support.v4.media.session.MediaSessionCompat.Callback"
        );
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        String fqcn = declaration.getQualifiedName();
        if (fqcn != null && mTargetClasses.contains(fqcn)) {
            boolean hasImplementation = false;
            for (UMethod method : declaration.findMethodsByName("onPlayFromSearch")) {
                List<UParameter> params = method.getParameterList().getParameters();
                if (params.size() == 2) {
                    String p1 = params.get(0).getType().getCanonicalText();
                    String p2 = params.get(1).getType().getCanonicalText();
                    if (p1.endsWith("String") && p2.endsWith("Bundle")) {
                        hasImplementation = true;
                        break;
                    }
                }
            }

            if (!hasImplementation) {
                context.report(ISSUE, declaration, context.getNameLocation(declaration),
                        "Missing `onPlayFromSearch(String, Bundle)` implementation required for Android Auto voice search support");
            }
        }
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UMethod method) {
        // Not used for this detector
    }
}
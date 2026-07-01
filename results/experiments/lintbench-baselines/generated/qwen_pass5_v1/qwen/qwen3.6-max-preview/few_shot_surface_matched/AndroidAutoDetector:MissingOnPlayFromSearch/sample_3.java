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
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingOnPlayFromSearch",
            "Missing onPlayFromSearch implementation",
            "To support voice searches on Android Auto, in addition to adding an intent-filter " +
            "for the action onPlayFromSearch, you also need to override and implement " +
            "onPlayFromSearch(String query, Bundle bundle).",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(AndroidAutoDetector.class, Scope.JAVA_FILE_SCOPE, Scope.MANIFEST_SCOPE));

    private final Set<String> classesWithIntentFilter = new HashSet<>();
    private final Set<String> classesWithMethod = new HashSet<>();

    @Override
    public boolean appliesTo(Context context, File file) {
        return true;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("action");
    }

    @Override
    public void beforeCheckRootProject(Context context) {
        classesWithIntentFilter.clear();
        classesWithMethod.clear();
    }

    @Override
    public void visitElement(XmlContext context, org.w3c.dom.Element element) {
        String actionName = element.getAttribute("android:name");
        if ("onPlayFromSearch".equals(actionName)) {
            org.w3c.dom.Node intentFilter = element.getParentNode();
            if (intentFilter != null) {
                org.w3c.dom.Node component = intentFilter.getParentNode();
                if (component != null && component.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                    String componentClass = ((org.w3c.dom.Element) component).getAttribute("android:name");
                    if (componentClass != null && !componentClass.isEmpty()) {
                        classesWithIntentFilter.add(componentClass);
                    }
                }
            }
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.service.media.MediaBrowserService");
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }

        boolean hasIntentFilter = false;
        for (String manifestClass : classesWithIntentFilter) {
            if (qualifiedName.equals(manifestClass) || qualifiedName.endsWith("." + manifestClass)) {
                hasIntentFilter = true;
                break;
            }
        }

        if (hasIntentFilter && !classesWithMethod.contains(qualifiedName)) {
            context.report(ISSUE, declaration, context.getNameLocation(declaration),
                    "This class is configured for Android Auto voice search but does not override " +
                    "`onPlayFromSearch(String, Bundle)`");
        }
    }

    @Override
    public void visitMethod(JavaContext context, UMethod method) {
        if ("onPlayFromSearch".equals(method.getName())) {
            UClass containingClass = method.getContainingUClass();
            if (containingClass != null && containingClass.getQualifiedName() != null) {
                classesWithMethod.add(containingClass.getQualifiedName());
            }
        }
    }
}
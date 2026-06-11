package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.AnnotationInfo;
import com.android.tools.lint.detector.api.AnnotationUsageInfo;
import com.android.tools.lint.detector.api.AnnotationUsageType;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.List;

public class AndroidAutoDetector extends Detector implements Detector.XmlScanner, Detector.SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingOnPlayFromSearch",
            "To support voice searches on Android Auto, you need to override and implement `onPlayFromSearch(String query, Bundle bundle)`.",
            "This issue reports cases where the method `onPlayFromSearch` is missing in your MediaBrowserService implementation.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(AndroidAutoDetector.class, true)
    );

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("onPlayFromSearch");
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return ResourceFolderType.MANIFEST == folderType;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if ("action".equals(element.getTagName())) {
            Attr nameAttr = element.getAttributeNode("android:name");
            if (nameAttr != null && "android.media.browse.MediaBrowserService".equals(nameAttr.getValue())) {
                boolean onPlayFromSearchFound = false;
                for (Element child : getChildren(element)) {
                    if ("action".equals(child.getTagName())) {
                        Attr actionNameAttr = child.getAttributeNode("android:name");
                        if (actionNameAttr != null && "android.media.browse.MediaBrowserService".equals(actionNameAttr.getValue())) {
                            onPlayFromSearchFound = true;
                            break;
                        }
                    }
                }

                if (!onPlayFromSearchFound) {
                    context.report(ISSUE, element, context.getLocation(element),
                            "Missing `onPlayFromSearch` method in MediaBrowserService implementation");
                }
            }
        }
    }

    private List<Element> getChildren(Element parent) {
        return Collections.emptyList(); // Implement as needed
    }

    @Override
    public void visitMethod(JavaContext context, UMethod method) {
        if ("onPlayFromSearch".equals(method.getName())) {
            PsiElement psi = method.getJavaPsi();
            if (psi instanceof PsiMethod && ((PsiMethod) psi).getParameterList().getParametersCount() == 2) {
                // Method is correctly implemented
                return;
            }
        }

        context.report(ISSUE, method, context.getLocation(method),
                "Missing `onPlayFromSearch(String query, Bundle bundle)` implementation");
    }

    @Override
    public List<Class<? extends PsiElement>> getApplicablePsiTypes() {
        return Collections.singletonList(PsiMethod.class);
    }

    @Override
    public void visitClass(JavaContext context, UClass clazz) {
        if (clazz.getQualifiedName().contains("MediaBrowserService")) {
            boolean onPlayFromSearchFound = false;
            for (UMethod method : clazz.getMethods()) {
                if ("onPlayFromSearch".equals(method.getName())) {
                    PsiElement psi = method.getJavaPsi();
                    if (psi instanceof PsiMethod && ((PsiMethod) psi).getParameterList().getParametersCount() == 2) {
                        onPlayFromSearchFound = true;
                        break;
                    }
                }
            }

            if (!onPlayFromSearchFound) {
                context.report(ISSUE, clazz, context.getLocation(clazz),
                        "Missing `onPlayFromSearch(String query, Bundle bundle)` implementation in MediaBrowserService");
            }
        }
    }
}
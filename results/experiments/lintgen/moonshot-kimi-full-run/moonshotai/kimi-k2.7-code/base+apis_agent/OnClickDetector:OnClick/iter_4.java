package com.android.tools.lint.checks;

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
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.UElementHandler;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OnClickDetector extends Detector implements Detector.XmlScanner, Detector.SourceCodeScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String TOOLS_URI = "http://schemas.android.com/tools";
    private static final String ATTR_ON_CLICK = "onClick";
    private static final String ATTR_CONTEXT = "context";
    private static final String VIEW_CLASS = "android.view.View";

    public static final Issue ISSUE = Issue.create(
            "OnClick",
            "onClick method does not exist",
            "The `onClick` attribute value should be the name of a method in this View's context "
                    + "to invoke when the view is clicked. This name must correspond to a public "
                    + "method that takes exactly one parameter of type `View`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    OnClickDetector.class,
                    EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE)
            )
    );

    private static class ClickHandler {
        final String name;
        final Location location;

        ClickHandler(String name, Location location) {
            this.name = name;
            this.location = location;
        }
    }

    private static class MethodSignature {
        final String name;
        final boolean isPublic;
        final boolean hasViewParameter;

        MethodSignature(String name, boolean isPublic, boolean hasViewParameter) {
            this.name = name;
            this.isPublic = isPublic;
            this.hasViewParameter = hasViewParameter;
        }
    }

    private final Map<Document, String> mDocumentContexts = new HashMap<>();
    private final Map<Document, List<ClickHandler>> mDocumentHandlers = new HashMap<>();
    private final Map<String, List<ClickHandler>> mClickHandlers = new HashMap<>();
    private final Map<String, List<MethodSignature>> mMethods = new HashMap<>();

    @Override
    public void beforeCheckProject(Context context) {
        mDocumentContexts.clear();
        mDocumentHandlers.clear();
        mClickHandlers.clear();
        mMethods.clear();
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(ATTR_ON_CLICK, ATTR_CONTEXT);
    }

    @Override
    public void visitAttribute(XmlContext context, Attr attribute) {
        String name = attribute.getLocalName();
        String uri = attribute.getNamespaceURI();
        if (name == null || uri == null) {
            return;
        }

        Document document = attribute.getOwnerDocument();
        if (ATTR_ON_CLICK.equals(name) && ANDROID_URI.equals(uri)) {
            String value = attribute.getValue();
            if (value != null && !value.isEmpty() && !value.startsWith("@") && !value.startsWith("?")) {
                List<ClickHandler> handlers = mDocumentHandlers.get(document);
                if (handlers == null) {
                    handlers = new ArrayList<>();
                    mDocumentHandlers.put(document, handlers);
                }
                handlers.add(new ClickHandler(value, context.getValueLocation(attribute)));
            }
        } else if (ATTR_CONTEXT.equals(name) && TOOLS_URI.equals(uri)) {
            String value = attribute.getValue();
            if (value != null && !value.isEmpty()) {
                mDocumentContexts.put(document, value);
            }
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.<Class<? extends UElement>>singletonList(UClass.class);
    }

    @Override
    public UElementHandler createUastHandler(JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(UClass node) {
                String className = node.getQualifiedName();
                if (className == null || className.isEmpty()) {
                    return;
                }

                List<MethodSignature> signatures = new ArrayList<>();
                for (UMethod method : node.getMethods()) {
                    PsiMethod psiMethod = method.getJavaPsi();
                    if (psiMethod == null) {
                        continue;
                    }

                    boolean isPublic = psiMethod.hasModifierProperty(PsiModifier.PUBLIC);
                    boolean hasViewParameter = false;
                    PsiParameterList parameterList = psiMethod.getParameterList();
                    if (parameterList.getParametersCount() == 1) {
                        PsiParameter parameter = parameterList.getParameters()[0];
                        PsiType parameterType = parameter.getType();
                        if (parameterType != null && VIEW_CLASS.equals(parameterType.getCanonicalText())) {
                            hasViewParameter = true;
                        }
                    }

                    signatures.add(new MethodSignature(psiMethod.getName(), isPublic, hasViewParameter));
                }

                if (!signatures.isEmpty()) {
                    List<MethodSignature> existing = mMethods.get(className);
                    if (existing == null) {
                        existing = new ArrayList<>();
                        mMethods.put(className, existing);
                    }
                    existing.addAll(signatures);
                }
            }
        };
    }

    @Override
    public void afterCheckProject(Context context) {
        for (Map.Entry<Document, List<ClickHandler>> docEntry : mDocumentHandlers.entrySet()) {
            String contextClass = mDocumentContexts.get(docEntry.getKey());
            if (contextClass == null || contextClass.isEmpty()) {
                continue;
            }

            List<ClickHandler> handlers = docEntry.getValue();
            List<ClickHandler> existing = mClickHandlers.get(contextClass);
            if (existing == null) {
                existing = new ArrayList<>();
                mClickHandlers.put(contextClass, existing);
            }
            existing.addAll(handlers);
        }

        for (Map.Entry<String, List<ClickHandler>> entry : mClickHandlers.entrySet()) {
            String className = entry.getKey();
            List<MethodSignature> methods = mMethods.get(className);

            for (ClickHandler handler : entry.getValue()) {
                boolean found = false;
                if (methods != null) {
                    for (MethodSignature method : methods) {
                        if (handler.name.equals(method.name) && method.isPublic && method.hasViewParameter) {
                            found = true;
                            break;
                        }
                    }
                }

                if (!found) {
                    String message = "The `onClick` method `" + handler.name
                            + "` does not exist in the context class `" + className + "`";
                    context.report(ISSUE, handler.location, message);
                }
            }
        }
    }
}
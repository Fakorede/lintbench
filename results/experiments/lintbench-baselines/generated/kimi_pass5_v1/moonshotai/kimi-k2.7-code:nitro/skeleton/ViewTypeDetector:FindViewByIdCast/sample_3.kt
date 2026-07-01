package com.android.tools.lint.checks;

import static com.android.SdkConstants.ATTR_CLASS;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.VIEW_TAG;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UastUtils;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class ViewTypeDetector extends ResourceXmlDetector implements Detector.JavaScanner {
    public static final Issue FIND_VIEW_BY_ID_CAST = Issue.create(...);
    private static final String FIND_VIEW_BY_ID = "findViewById";
    private Map<ResourceUrl, String> mIdToViewType;

    @Override public boolean appliesTo(ResourceFolderType folderType) { ... }
    @Override public Collection<String> getApplicableAttributes() { ... }
    @Override public void visitAttribute(XmlContext context, Attr attribute) { ... }
    @Override public List<String> getApplicableMethodNames() { ... }
    @Override public void visitMethodCall(JavaContext context, UCallExpression node, PsiMethod method) { ... }
}
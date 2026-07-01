package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_CLASS;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PARENT;
import static com.android.SdkConstants.ATTR_STYLE;
import static com.android.SdkConstants.TAG_ITEM;
import static com.android.SdkConstants.TAG_STYLE;
import static com.android.SdkConstants.TAG_VIEW;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UThisExpression;
import org.jetbrains.uast.UastUtils;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class RequiredAttributeDetector extends LayoutDetector implements SourceCodeScanner, XmlScanner {

    private static final String SET_LAYOUT_PARAMS = "setLayoutParams";

    public static final Issue ISSUE =
            Issue.create(
                    "RequiredSize",
                    "Missing Layout Width or Height",
                    "All views must specify an explicit `layout_width` and `layout_height`"
                            + " attribute. There is a runtime check for this, so if you fail to"
                            + " specify a size, an exception is thrown at runtime. It's possible"
                            + " to specify these widths via styles as well. GridLayout, as a"
                            + " special case, does not require you to specify a size.",
                    Category.CORRECTNESS,
                    8,
                    Severity.ERROR,
                    new Implementation(
                            RequiredAttributeDetector.class,
                            Scope.JAVA_FILE_SCOPE,
                            Scope.RESOURCE_FILE_SCOPE));

    private final List<Candidate> mCandidates = new ArrayList<>();
    private final Map<String, StyleInfo> mStyles = new HashMap<>();
    private final Set<String> mCodeSizedClasses = new HashSet<>();

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        String name = file.getName();
        return super.appliesTo(context, file) || name.endsWith(".java") || name.endsWith(".xml");
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (Candidate candidate : mCandidates) {
            if (sizeSuppliedByStyle(candidate) || sizeSetInCode(candidate.viewClass)) {
                continue;
            }
            context.report(ISSUE, candidate.location, buildMessage(candidate.missing));
        }
        mCandidates.clear();
        mStyles.clear();
        mCodeSizedClasses.clear();
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();

        if (TAG_STYLE.equals(tag)) {
            String name = element.getAttribute(ATTR_NAME);
            if (name != null && !name.isEmpty()) {
                StyleInfo info = getStyleInfo(name);
                String parent = element.getAttribute(ATTR_PARENT);
                info.parent = normalizeStyleName(parent);
                if (info.parent.isEmpty() && name.contains(".")) {
                    info.parent = name.substring(0, name.lastIndexOf('.'));
                }
            }
            return;
        }

        if (TAG_ITEM.equals(tag)) {
            Node parentNode = element.getParentNode();
            if (parentNode instanceof Element
                    && TAG_STYLE.equals(((Element) parentNode).getTagName())) {
                String styleName = ((Element) parentNode).getAttribute(ATTR_NAME);
                String itemName = element.getAttribute(ATTR_NAME);
                if (styleName != null
                        && !styleName.isEmpty()
                        && itemName != null
                        && !itemName.isEmpty()) {
                    StyleInfo info = getStyleInfo(styleName);
                    if (itemName.endsWith(ATTR_LAYOUT_WIDTH)) {
                        info.attrs.add(ATTR_LAYOUT_WIDTH);
                    } else if (itemName.endsWith(ATTR_LAYOUT_HEIGHT)) {
                        info.attrs.add(ATTR_LAYOUT_HEIGHT);
                    }
                }
            }
            return;
        }

        String viewClass = getViewClass(tag, element);
        if (viewClass == null || isGridLayout(viewClass)) {
            return;
        }

        boolean hasWidth = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
        boolean hasHeight = element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);
        if (hasWidth && hasHeight) {
            return;
        }

        Set<String> missing = new LinkedHashSet<>();
        if (!hasWidth) {
            missing.add(ATTR_LAYOUT_WIDTH);
        }
        if (!hasHeight) {
            missing.add(ATTR_LAYOUT_HEIGHT);
        }

        String styleRef = element.getAttribute(ATTR_STYLE);
        if (styleRef != null && !styleRef.isEmpty()) {
            mCandidates.add(
                    new Candidate(context.getLocation(element), viewClass, missing, styleRef));
        } else {
            context.report(
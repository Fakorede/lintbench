package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class EllipsizeMaxLinesDetector extends LayoutDetector {

    private static final String ATTR_MAX_LINES = "maxLines";
    private static final String ATTR_ELLIPSIZE = "ellipsize";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String ERROR_MESSAGE =
            "Combining `ellipsize` and `maxLines=1` can lead to crashes on some devices. "
                    + "Use `singleLine=true` or remove `ellipsize`.";

    private static final Implementation IMPLEMENTATION =
            new Implementation(EllipsizeMaxLinesDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "EllipsizeMaxLines",
                    "Combining Ellipsize and Maxlines",
                    "Combining `ellipsize` and `maxLines=1` can lead to crashes on some devices. "
                            + "Earlier versions of lint recommended replacing `singleLine=true` with "
                            + "`maxLines=1`, but that should not be done when using `ellipsize`. "
                            + "Use `singleLine=true` when you need `ellipsize`, or remove `ellipsize`.",
                    Category.CORRECTNESS,
                    4,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private final Map<Element, NodeState> mNodeStates = new IdentityHashMap<>();

    private static final class NodeState {
        boolean hasMaxLines;
        boolean hasEllipsize;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mNodeStates.clear();
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        mNodeStates.clear();
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(ATTR_MAX_LINES, ATTR_ELLIPSIZE);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        Element element = attribute.getOwnerElement();
        NodeState state = mNodeStates.get(element);
        if (state == null) {
            state = new NodeState();
            mNodeStates.put(element, state);
        }

        String name = attribute.getLocalName();
        String value = attribute.getValue();

        if (ATTR_MAX_LINES.equals(name) && value != null && "1".equals(value.trim())) {
            state.hasMaxLines = true;
        } else if (ATTR_ELLIPSIZE.equals(name) && value != null && !value.trim().isEmpty()) {
            state.hasEllipsize = true;
        }

        if (state.hasMaxLines && state.hasEllipsize) {
            context.report(
                    ISSUE, attribute, context.getValueLocation(attribute), ERROR_MESSAGE);
            mNodeStates.remove(element);
        }
    }
}
package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_NS_NAME;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_ABOVE;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_BASELINE;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_BOTTOM;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_END;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_LEFT;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_BOTTOM;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_END;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_LEFT;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_RIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_START;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_TOP;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_RIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_START;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_TOP;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_WITH_PARENT_IF_MISSING;
import static com.android.SdkConstants.ATTR_LAYOUT_BELOW;
import static com.android.SdkConstants.ATTR_LAYOUT_CENTER_HORIZONTAL;
import static com.android.SdkConstants.ATTR_LAYOUT_CENTER_IN_PARENT;
import static com.android.SdkConstants.ATTR_LAYOUT_CENTER_VERTICAL;
import static com.android.SdkConstants.ATTR_LAYOUT_COLUMN;
import static com.android.SdkConstants.ATTR_LAYOUT_COLUMN_SPAN;
import static com.android.SdkConstants.ATTR_LAYOUT_GRAVITY;
import static com.android.SdkConstants.ATTR_LAYOUT_TO_END_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_TO_LEFT_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_TO_RIGHT_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_TO_START_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_WEIGHT;
import static com.android.SdkConstants.FN_ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.GRID_LAYOUT;
import static com.android.SdkConstants.LINEAR_LAYOUT;
import static com.android.SdkConstants.RELATIVE_LAYOUT;
import static com.android.SdkConstants.TABLE_ROW;
import static com.android.SdkConstants.TAG_INCLUDE;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class ObsoleteLayoutParamsDetector extends LayoutDetector {
    public static final Issue ISSUE =
            Issue.create(
                    "ObsoleteLayoutParam",
                    "Obsolete layout params",
                    "The given `layout_param` is not defined for the given layout, meaning it has "
                            + "no effect. This usually happens when you change the parent layout "
                            + "or move view code around without updating the layout params. This "
                            + "will cause useless attribute processing at runtime, and is "
                            + "misleading for others reading the layout so the parameter should "
                            + "be removed.",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    new Implementation(ObsoleteLayoutParamsDetector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final Map<String, Set<String>> ATTRS_BY_PARENT = Maps.newHashMap();

    static {
        ATTRS_BY_PARENT.put(
                LINEAR_LAYOUT,
                Sets.newHashSet(ATTR_LAYOUT_WEIGHT, ATTR_LAYOUT_GRAVITY));
        ATTRS_BY_PARENT.put(
                RELATIVE_LAYOUT,
                Sets.newHashSet(
                        ATTR_LAYOUT_ABOVE,
                        ATTR_LAYOUT_ALIGN_BASELINE,
                        ATTR_LAYOUT_ALIGN_BOTTOM,
                        ATTR_LAYOUT_ALIGN_END,
                        ATTR_LAYOUT_ALIGN_LEFT,
                        ATTR_LAYOUT_ALIGN_PARENT_BOTTOM,
                        ATTR_LAYOUT_ALIGN_PARENT_END,
                        ATTR_LAYOUT_ALIGN_PARENT_LEFT,
                        ATTR_LAYOUT_ALIGN_PARENT_RIGHT,
                        ATTR_LAYOUT_ALIGN_PARENT_START,
                        ATTR_LAYOUT_ALIGN_PARENT_TOP,
                        ATTR_LAYOUT_ALIGN_RIGHT,
                        ATTR_LAYOUT_ALIGN_START,
                        ATTR_LAYOUT_ALIGN_TOP,
                        ATTR_LAYOUT_ALIGN_WITH_PARENT_IF_MISSING,
                        ATTR_LAYOUT_BELOW,
                        ATTR_LAYOUT_CENTER_HORIZONTAL,
                        ATTR_LAYOUT_CENTER_IN_PARENT,
                        ATTR_LAYOUT_CENTER_VERTICAL,
                        ATTR_LAYOUT_TO_END_OF,
                        ATTR_LAYOUT_TO_LEFT_OF,
                        ATTR_LAYOUT_TO_RIGHT_OF,
                        ATTR_LAYOUT_TO_START_OF,
                        ATTR_LAYOUT_GRAVITY));
        ATTRS_BY_PARENT.put(
                GRID_LAYOUT,
                Sets.newHashSet(
                        ATTR_LAYOUT_COLUMN,
                        ATTR_LAYOUT_COLUMN_SPAN,
                        ATTR_LAYOUT_GRAVITY,
                        ATTR_LAYOUT_ROW,
                        ATTR_LAYOUT_ROW_SPAN));
        ATTRS_BY_PARENT.put(
                TABLE_ROW,
                Sets.newHashSet(ATTR_LAYOUT_COLUMN, ATTR_LAYOUT_SPAN));
    }

    private static final String ATTR_LAYOUT_SPAN = "layout_span";
    private static final String ATTR_LAYOUT_ROW = "layout_row";
    private static final String ATTR_LAYOUT_ROW_SPAN = "layout_rowSpan";

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Element parent = (Element) element.getParentNode();
        if (parent == null) {
            return;
        }

        String parentName = parent.getTagName();
        if (parentName == null) {
            return;
        }
        if (parentName.equals(TAG_INCLUDE) || parentName.equals(FN_ANDROID_MANIFEST_XML)) {
            return;
        }

        int index = parentName.indexOf('.');
        if (index != -1) {
            parentName = parentName.substring(index + 1);
        }

        Set<String> allowed = ATTRS_BY_PARENT.get(parentName);
        if (allowed == null) {
            return;
        }

        for (Attr attribute : getAttributes(element)) {
            String namespace = attribute.getNamespaceURI();
            if (namespace == null || !namespace.equals(ANDROID_URI)) {
                continue;
            }

            String localName = attribute.getLocalName();
            if (localName == null || !localName.startsWith("layout_")) {
                continue;
            }

            if (!allowed.contains(localName)) {
                String message =
                        String.format(
                                "Invalid layout param in a `%1$s`: `%2$s:%3$s`",
                                parentName, ANDROID_NS_NAME, localName);
                context.report(ISSUE, attribute, context.getValueLocation(attribute), message);
            }
        }
    }

    @NonNull
    private static Iterable<Attr> getAttributes(@NonNull Element element) {
        if (!element.hasAttributes()) {
            return Collections.emptyList();
        }
        return new Iterable<Attr>() {
            @Override
            public java.util.Iterator<Attr> iterator() {
                return new Iterator(element);
            }
        };
    }

    private static final class Iterator implements java.util.Iterator<Attr> {
        private final org.w3c.dom.NamedNodeMap mNodes;
        private int mIndex;
        private final int mLength;

        private Iterator(@NonNull Element element) {
            mNodes = element.getAttributes();
            mLength = mNodes.getLength();
        }

        @Override
        public boolean hasNext() {
            return mIndex < mLength;
        }

        @Override
        public Attr next() {
            if (mIndex >= mLength) {
                throw new java.util.NoSuchElementException();
            }
            return (Attr) mNodes.item(mIndex++);
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
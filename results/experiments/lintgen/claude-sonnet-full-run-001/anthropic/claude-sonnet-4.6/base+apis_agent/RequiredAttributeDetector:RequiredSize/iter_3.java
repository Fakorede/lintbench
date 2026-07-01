String styleRef = element.getAttribute(ATTR_STYLE);
if (styleRef != null && !styleRef.isEmpty()) {
    String styleName = normalizeStyleRef(styleRef);
    if (styleName != null && mStyleToAttributes != null) {
        // Check if style provides the missing attributes
        boolean styleHasWidth = !missingWidth || styleDefinesAttribute(styleName, ATTR_LAYOUT_WIDTH);
        boolean styleHasHeight = !missingHeight || styleDefinesAttribute(styleName, ATTR_LAYOUT_HEIGHT);
        if (styleHasWidth && styleHasHeight) {
            return; // Style provides all missing attributes
        }
        // Style doesn't provide all missing attributes - but maybe it's defined later
        // Defer to afterCheckRootProject
        if (mPendingElements == null) {
            mPendingElements = new ArrayList<>();
        }
        mPendingElements.add(new PendingElement(context, element, styleName, !hasWidth, !hasHeight));
        return;
    } else if (styleName != null) {
        // Style not yet processed - defer
        if (mPendingElements == null) {
            mPendingElements = new ArrayList<>();
        }
        mPendingElements.add(new PendingElement(context, element, styleName, !hasWidth, !hasHeight));
        return;
    }
    // Can't resolve style reference - report warning
}
reportMissing(context, element, !hasWidth, !hasHeight);
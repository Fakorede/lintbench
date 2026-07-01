private static void checkSymmetry(XmlContext context, Attr attribute, String name) {
    String counterpart;
    String all;
    String horizontal;

    if (ATTR_PADDING_LEFT.equals(name)) {
        counterpart = ATTR_PADDING_RIGHT;
        all = ATTR_PADDING;
        horizontal = ATTR_PADDING_HORIZONTAL;
    } else if (ATTR_PADDING_RIGHT.equals(name)) {
        counterpart = ATTR_PADDING_LEFT;
        all = ATTR_PADDING;
        horizontal = ATTR_PADDING_HORIZONTAL;
    } else if (ATTR_PADDING_START.equals(name)) {
        counterpart = ATTR_PADDING_END;
        all = ATTR_PADDING;
        horizontal = ATTR_PADDING_HORIZONTAL;
    } else if (ATTR_PADDING_END.equals(name)) {
        counterpart = ATTR_PADDING_START;
        all = ATTR_PADDING;
        horizontal = ATTR_PADDING_HORIZONTAL;
    } else if (ATTR_LAYOUT_MARGIN_LEFT.equals(name)) {
        counterpart = ATTR_LAYOUT_MARGIN_RIGHT;
        all = ATTR_LAYOUT_MARGIN;
        horizontal = ATTR_LAYOUT_MARGIN_HORIZONTAL;
    } else if (ATTR_LAYOUT_MARGIN_RIGHT.equals(name)) {
        counterpart = ATTR_LAYOUT_MARGIN_LEFT;
        all = ATTR_LAYOUT_MARGIN;
        horizontal = ATTR_LAYOUT_MARGIN_HORIZONTAL;
    } else if (ATTR_LAYOUT_MARGIN_START.equals(name)) {
        counterpart = ATTR_LAYOUT_MARGIN_END;
        all = ATTR_LAYOUT_MARGIN;
        horizontal = ATTR_LAYOUT_MARGIN_HORIZONTAL;
    } else if (ATTR_LAYOUT_MARGIN_END.equals(name)) {
        counterpart = ATTR_LAYOUT_MARGIN_START;
        all = ATTR_LAYOUT_MARGIN;
        horizontal = ATTR_LAYOUT_MARGIN_HORIZONTAL;
    } else {
        return;
    }

    Element element = attribute.getOwnerElement();
    if (!element.hasAttributeNS(ANDROID_URI, counterpart)
            && !element.hasAttributeNS(ANDROID_URI, all)
            && !element.hasAttributeNS(ANDROID_URI, horizontal)) {
        String message = String.format(
                "When you define %1$s you should probably also define %2$s for right-to-left symmetry",
                name, counterpart);
        context.report(ISSUE_SYMMETRY, attribute, context.getLocation(attribute), message);
    }
}
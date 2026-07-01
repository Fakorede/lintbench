private void checkElement(XmlContext context, Element element, int effectiveSdk) {
        String tagName = element.getTagName();

        if (effectiveSdk < 24) {
            if ("gradient".equals(tagName)) {
                context.report(ISSUE, element, context.getLocation(element),
                        "Resource folder-based image generation does not support `<gradient>`");
                return;
            }
        }

        if (effectiveSdk < 21) {
            if ("clip-path".equals(tagName)) {
                context.report(ISSUE, element, context.getLocation(element),
                        "Resource folder-based image generation does not support `<clip-path>`");
            }
            if ("vector".equals(tagName)) {
                Attr widthAttr = getAttributeNode(element, "http://schemas.android.com/apk/res/android", "width");
                if (widthAttr != null) {
                    double width = getDpValue(widthAttr.getValue());
                    if (width > 200) {
                        context.report(ISSUE, widthAttr, context.getLocation(widthAttr),
                                "Limit vector icons to 200x200 dp to avoid wasting memory");
                    }
                }
                Attr heightAttr = getAttributeNode(element, "http://schemas.android.com/apk/res/android", "height");
                if (heightAttr != null) {
                    double height = getDpValue(heightAttr.getValue());
                    if (height > 200) {
                        context.report(ISSUE, heightAttr, context.getLocation(heightAttr),
                                "Limit vector icons to 200x200 dp to avoid wasting memory");
                    }
                }
            }
        }
state.restrictionCount++
        if (state.restrictionCount > MAX_NUMBER_OF_NESTED_RESTRICTIONS) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Number of nested restrictions exceeds $MAX_NUMBER_OF_NESTED_RESTRICTIONS"
            )
            return
        }
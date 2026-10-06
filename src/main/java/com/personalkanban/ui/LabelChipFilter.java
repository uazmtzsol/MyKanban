package com.personalkanban.ui;

/**
 * Pure decision behind clicking a label chip (user request, session 9):
 * the clicked label becomes the label filter, and clicking the label that is
 * already filtering the board clears it — so a chip behaves as a toggle.
 *
 * <p>Kept free of JavaFX so the behavior can be unit-tested without a
 * toolkit; {@link BoardController#onFilterByLabel} only applies the result to
 * the filter field.</p>
 */
final class LabelChipFilter {

    private LabelChipFilter() {
    }

    /**
     * @param currentFilter the text currently in the label filter field
     *                      (may be null or blank)
     * @param clickedLabel  the label of the clicked chip (may be null/blank)
     * @return the filter text the field should now hold: the clicked label, or
     *         {@code ""} when the same label already filtered the board (and,
     *         for a null/blank click, the unchanged current text)
     */
    static String toggle(String currentFilter, String clickedLabel) {
        String current = currentFilter == null ? "" : currentFilter;
        if (clickedLabel == null || clickedLabel.isBlank()) {
            return current;
        }
        String label = clickedLabel.strip();
        return current.strip().equalsIgnoreCase(label) ? "" : label;
    }
}

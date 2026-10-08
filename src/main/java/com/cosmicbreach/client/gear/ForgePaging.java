package com.cosmicbreach.client.gear;

/**
 * The Forge screen's recipe grid paging (1.1 design section 10), pure: a page is {@value ForgeLayout#COLS} by
 * {@value ForgeLayout#ROWS} cells over the open tab's list, scrolled a whole row at a time. A recipe is named by its
 * position in that list, a cell by its place on the page, and {@code scroll} is the row the page starts at. Today's
 * biggest tab fits one page, so none of this runs in a game yet; the first tab past 20 recipes meets it.
 */
public final class ForgePaging {
    private static final int PAGE = ForgeLayout.COLS * ForgeLayout.ROWS;

    private ForgePaging() {
    }

    /** The rows {@code count} recipes take. */
    public static int rows(int count) {
        return (count + ForgeLayout.COLS - 1) / ForgeLayout.COLS;
    }

    /** The furthest the grid scrolls for {@code count} recipes: the rows that don't fit on one page. */
    public static int maxScroll(int count) {
        return Math.max(0, rows(count) - ForgeLayout.ROWS);
    }

    /** {@code scroll} brought into range for {@code count} recipes (a shorter list has less to scroll). */
    public static int clamp(int scroll, int count) {
        return Math.max(0, Math.min(scroll, maxScroll(count)));
    }

    /** The scroll that shows list position {@code position}: {@code scroll} if it is on the page already, else the nearest page. */
    public static int scrollToShow(int scroll, int position, int count) {
        int row = position / ForgeLayout.COLS;
        if (row < scroll) {
            return clamp(row, count);
        }
        if (row >= scroll + ForgeLayout.ROWS) {
            return clamp(row - ForgeLayout.ROWS + 1, count);
        }
        return clamp(scroll, count);
    }

    /** The cell of the page at {@code scroll} that shows list position {@code position}, or -1 if it is off the page. */
    public static int cellOf(int position, int scroll) {
        int cell = position - scroll * ForgeLayout.COLS;
        return cell >= 0 && cell < PAGE ? cell : -1;
    }

    /** The list position shown in {@code cell} of the page at {@code scroll}, or -1 when that cell is empty. */
    public static int positionAt(int cell, int scroll, int count) {
        int position = scroll * ForgeLayout.COLS + cell;
        return cell >= 0 && cell < PAGE && position < count ? position : -1;
    }

    /** The scroll after a turn of the mouse wheel (positive turns up, toward the first row): a whole row, whatever the turn. */
    public static int wheel(int scroll, double turn, int count) {
        return clamp(scroll - (int) Math.signum(turn), count);
    }

    /** The scroll for a drag that has the mouse {@code fraction} of the way down the bar's track (0 top, 1 bottom). */
    public static int dragTo(float fraction, int count) {
        return clamp(Math.round(fraction * maxScroll(count)), count);
    }

    /** How far down a track of {@code track} pixels the bar's {@code thumb} sits at {@code scroll}: the top if nothing scrolls. */
    public static int thumbOffset(int scroll, int count, int track, int thumb) {
        int max = maxScroll(count);
        return max == 0 ? 0 : (int) ((track - thumb) * (scroll / (float) max));
    }
}

package com.cosmicbreach.client.gear;

import static com.cosmicbreach.client.gear.ForgeLayout.COLS;
import static com.cosmicbreach.client.gear.ForgeLayout.ROWS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The recipe grid's paging (1.1 design section 10) for a tab with more recipes than one page holds: a page is
 * {@value ForgeLayout#COLS} by {@value ForgeLayout#ROWS} cells, so no tab of today's 29 recipes scrolls, but the first
 * tab past 20 recipes must. Everything here is about the 21 to 45 recipes a full tab can come to.
 */
class ForgePagingTest {
    private static final int PAGE = COLS * ROWS;

    @Test
    void aTabOfOnePageOrLessNeverScrolls() {
        for (int count = 0; count <= PAGE; count++) {
            assertEquals(0, ForgePaging.maxScroll(count), count + " recipes");
        }
    }

    @Test
    void everyRowPastTheFourthIsOneMoreScroll() {
        assertEquals(1, ForgePaging.maxScroll(21), "one recipe on a fifth row");
        assertEquals(1, ForgePaging.maxScroll(25), "a full fifth row");
        assertEquals(2, ForgePaging.maxScroll(26));
        assertEquals(5, ForgePaging.maxScroll(45), "nine rows, four of them showing");
        assertEquals(5, ForgePaging.rows(21));
        assertEquals(9, ForgePaging.rows(45));
        assertEquals(0, ForgePaging.rows(0));
    }

    @Test
    void aCellIsTheRecipeAtItsRowOfThePageOrNothing() {
        assertEquals(0, ForgePaging.positionAt(0, 0, 23));
        assertEquals(19, ForgePaging.positionAt(PAGE - 1, 0, 23), "the last cell of the first page");
        assertEquals(5, ForgePaging.positionAt(0, 1, 23), "scrolled one row, the top left cell is the sixth recipe");
        assertEquals(22, ForgePaging.positionAt(17, 1, 23), "the last recipe sits in the third cell of the last row");
        assertEquals(-1, ForgePaging.positionAt(18, 1, 23), "past the end the cell is empty");
        assertEquals(-1, ForgePaging.positionAt(19, 1, 23));
    }

    @Test
    void aRecipeIsOnThePageOrOffIt() {
        assertEquals(7, ForgePaging.cellOf(7, 0));
        assertEquals(2, ForgePaging.cellOf(7, 1), "scrolled a row, position 7 is in the first row");
        assertEquals(-1, ForgePaging.cellOf(4, 1), "a row above the page");
        assertEquals(-1, ForgePaging.cellOf(25, 1), "a row below the page");
        assertEquals(19, ForgePaging.cellOf(24, 1), "the last cell of the page");
    }

    @Test
    void choosingARecipeScrollsTheNearestWayToShowIt() {
        assertEquals(0, ForgePaging.scrollToShow(0, 19, 45), "already on the first page");
        assertEquals(1, ForgePaging.scrollToShow(0, 20, 45), "the first recipe of the fifth row: one row down");
        assertEquals(3, ForgePaging.scrollToShow(0, 30, 45), "the seventh row becomes the last of the page");
        assertEquals(5, ForgePaging.scrollToShow(0, 44, 45), "the last recipe: the furthest scroll");
        assertEquals(2, ForgePaging.scrollToShow(5, 10, 45), "from the bottom up to the third row: it becomes the first of the page");
        assertEquals(2, ForgePaging.scrollToShow(2, 20, 45), "inside the window, nothing moves");
        assertEquals(2, ForgePaging.scrollToShow(2, 29, 45), "the window's last row too");
        assertEquals(3, ForgePaging.scrollToShow(2, 34, 45), "one row past the window: one scroll");
    }

    @Test
    void everyRecipeOfAFullTabCanBeBroughtOntoThePageAndFoundThere() {
        for (int count = PAGE + 1; count <= 45; count++) {
            for (int position = 0; position < count; position++) {
                int scroll = ForgePaging.scrollToShow(0, position, count);
                assertTrue(scroll >= 0 && scroll <= ForgePaging.maxScroll(count), count + " recipes, position " + position + ": " + scroll);
                int cell = ForgePaging.cellOf(position, scroll);
                assertTrue(cell >= 0 && cell < PAGE, count + " recipes, position " + position + " is off the page");
                assertEquals(position, ForgePaging.positionAt(cell, scroll, count), "the cell and the recipe agree");
            }
        }
    }

    @Test
    void theFurthestScrollIsTheFirstOneThatShowsTheLastRecipe() {
        for (int count = PAGE + 1; count <= 45; count++) {
            int last = ForgePaging.maxScroll(count);
            assertTrue(ForgePaging.cellOf(count - 1, last) >= 0, count + " recipes: the last one shows at the furthest scroll");
            assertEquals(-1, ForgePaging.cellOf(count - 1, last - 1), count + " recipes: one scroll short, the last is still below the page");
        }
    }

    @Test
    void theWheelMovesOneRowAndStopsAtBothEnds() {
        assertEquals(0, ForgePaging.wheel(0, 1.0, 45), "wheel up at the top");
        assertEquals(1, ForgePaging.wheel(0, -1.0, 45), "wheel down");
        assertEquals(2, ForgePaging.wheel(3, 1.0, 45), "wheel up");
        assertEquals(5, ForgePaging.wheel(5, -1.0, 45), "wheel down at the bottom");
        assertEquals(1, ForgePaging.wheel(0, -0.2, 45), "a small turn still moves a whole row");
        assertEquals(2, ForgePaging.wheel(2, 0.0, 45), "no turn, no move");
        assertEquals(0, ForgePaging.wheel(0, -1.0, 20), "a tab that fits never scrolls");
    }

    @Test
    void draggingTheBarMapsItsTrackToEveryScroll() {
        assertEquals(0, ForgePaging.dragTo(-0.3f, 45), "above the track");
        assertEquals(0, ForgePaging.dragTo(0.0f, 45));
        assertEquals(5, ForgePaging.dragTo(1.0f, 45));
        assertEquals(5, ForgePaging.dragTo(1.4f, 45), "below the track");
        assertEquals(3, ForgePaging.dragTo(0.5f, 45), "halfway of five rounds up to three");
        assertEquals(0, ForgePaging.dragTo(0.9f, 20), "a tab that fits has nothing to drag");
        for (int scroll = 0; scroll <= 5; scroll++) {
            assertEquals(scroll, ForgePaging.dragTo(scroll / 5.0f, 45), "the track point of scroll " + scroll + " drags back to it");
        }
    }

    @Test
    void theBarsThumbSitsAtTheTopWhenThereIsNothingToScrollAndTravelsTheTrackOtherwise() {
        int track = 71;
        int thumb = 15;
        assertEquals(0, ForgePaging.thumbOffset(0, 20, track, thumb), "nothing to scroll");
        assertEquals(0, ForgePaging.thumbOffset(0, 45, track, thumb));
        assertEquals(track - thumb, ForgePaging.thumbOffset(5, 45, track, thumb), "the furthest scroll puts it at the bottom");
        assertEquals(28, ForgePaging.thumbOffset(1, 26, track, thumb), "2 scrolls of range, one in: the middle (56 / 2)");
        int previous = -1;
        for (int scroll = 0; scroll <= 5; scroll++) {
            int offset = ForgePaging.thumbOffset(scroll, 45, track, thumb);
            assertTrue(offset > previous, "the thumb moves down with every row");
            previous = offset;
        }
    }

    @Test
    void aScrollLeftOverFromALongerListIsBroughtBackIntoRange() {
        assertEquals(1, ForgePaging.clamp(4, 21), "a shorter list has less to scroll");
        assertEquals(0, ForgePaging.clamp(3, 20));
        assertEquals(0, ForgePaging.clamp(-2, 45));
        assertEquals(3, ForgePaging.clamp(3, 45), "in range stays");
    }
}

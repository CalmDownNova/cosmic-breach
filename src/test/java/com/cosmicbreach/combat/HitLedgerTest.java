package com.cosmicbreach.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HitLedgerTest {

    @Test
    void byDefaultATargetIsHitOncePerUse() {
        HitLedger ledger = new HitLedger();
        assertTrue(ledger.tryHit(1, 42, 100, 1, 0));
        assertFalse(ledger.tryHit(1, 42, 101, 1, 0), "the second active tick does not hit again");
        assertTrue(ledger.tryHit(1, 7, 101, 1, 0), "another target still can");
        assertTrue(ledger.tryHit(2, 42, 110, 1, 0), "the next use can");
        assertEquals(1, ledger.hitsOn(1, 42));
    }

    @Test
    void multiHitMovesRespectCountAndInterval() {
        HitLedger ledger = new HitLedger();
        assertTrue(ledger.tryHit(5, 1, 0, 3, 4));
        assertFalse(ledger.tryHit(5, 1, 2, 3, 4), "too soon");
        assertTrue(ledger.tryHit(5, 1, 4, 3, 4));
        assertTrue(ledger.tryHit(5, 1, 8, 3, 4));
        assertFalse(ledger.tryHit(5, 1, 12, 3, 4), "three hits used");
        assertEquals(3, ledger.hitsOn(5, 1));
    }

    @Test
    void aZeroIntervalMeansOncePerTick() {
        HitLedger ledger = new HitLedger();
        assertTrue(ledger.tryHit(1, 1, 10, 2, 0));
        assertFalse(ledger.tryHit(1, 1, 10, 2, 0));
        assertTrue(ledger.tryHit(1, 1, 11, 2, 0));
    }

    @Test
    void forgettingAUseClearsIt() {
        HitLedger ledger = new HitLedger();
        ledger.tryHit(1, 1, 0, 1, 0);
        ledger.forget(1);
        assertEquals(0, ledger.hitsOn(1, 1));
        assertTrue(ledger.tryHit(1, 1, 1, 1, 0));
    }

    @Test
    void spreadHitsMayLandSeveralInOneTickUpToTheirCount() {
        HitLedger ledger = new HitLedger();
        assertEquals(1, ledger.tryHits(3, 9, 10, 4, 1), "the Gyre's first tick");
        assertEquals(2, ledger.tryHits(3, 9, 11, 4, 2), "two in its second");
        assertEquals(1, ledger.tryHits(3, 9, 12, 4, 3), "only one left of four");
        assertEquals(0, ledger.tryHits(3, 9, 13, 4, 1));
        assertEquals(4, ledger.hitsOn(3, 9));
        assertEquals(2, ledger.tryHits(3, 5, 12, 4, 2), "a target that came in late takes that tick's share");
        assertEquals(0, ledger.tryHits(3, 5, 12, 4, 0), "none due");
    }

    @Test
    void onlyRecentUsesAreKept() {
        HitLedger ledger = new HitLedger();
        for (int serial = 1; serial <= 20; serial++) {
            ledger.tryHit(serial, 1, serial, 1, 0);
        }
        assertEquals(0, ledger.hitsOn(1, 1), "old uses are dropped");
        assertEquals(1, ledger.hitsOn(20, 1));
    }
}

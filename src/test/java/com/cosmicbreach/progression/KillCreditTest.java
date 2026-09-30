package com.cosmicbreach.progression;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Who shares a kill (GDD section 3.2): whoever dealt damage, or stood within 24 blocks. */
class KillCreditTest {
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final UUID C = new UUID(0, 3);
    private static final UUID D = new UUID(0, 4);

    @Test
    void damagersCountWhereverTheyAre() {
        Set<UUID> result = KillCredit.participants(List.of(A), List.of(new KillCredit.Bystander(A, 300.0, true)));
        assertEquals(Set.of(A), result, "a damager 300 blocks away still took part");
        assertEquals(Set.of(B), KillCredit.participants(List.of(B), List.of()), "or in another dimension entirely");
    }

    @Test
    void bystandersCountWithin24Blocks() {
        Set<UUID> result = KillCredit.participants(List.of(), List.of(
                new KillCredit.Bystander(A, 3.0, true),
                new KillCredit.Bystander(B, 24.0, true),
                new KillCredit.Bystander(C, 24.01, true)));
        assertEquals(Set.of(A, B), result);
    }

    @Test
    void spectatorsAndTheDeadDontCount() {
        Set<UUID> result = KillCredit.participants(List.of(), List.of(new KillCredit.Bystander(A, 2.0, false)));
        assertTrue(result.isEmpty());
    }

    @Test
    void everyoneOnceDamagersFirst() {
        Set<UUID> result = KillCredit.participants(List.of(C, A), List.of(
                new KillCredit.Bystander(A, 5.0, true),
                new KillCredit.Bystander(D, 5.0, true),
                new KillCredit.Bystander(B, 40.0, true)));
        assertEquals(List.of(C, A, D), List.copyOf(result));
    }

    @Test
    void creditRemembersEachDamagerOnce() {
        KillCredit credit = new KillCredit();
        credit.recordDamage(A);
        credit.recordDamage(B);
        credit.recordDamage(A);
        assertEquals(List.of(A, B), List.copyOf(credit.damagers()));
    }

    @Test
    void creditStopsGrowingAtTheLimit() {
        KillCredit credit = new KillCredit();
        for (int i = 0; i < KillCredit.MAX_TRACKED + 10; i++) {
            credit.recordDamage(new UUID(1, i));
        }
        assertEquals(KillCredit.MAX_TRACKED, credit.damagers().size());
    }
}

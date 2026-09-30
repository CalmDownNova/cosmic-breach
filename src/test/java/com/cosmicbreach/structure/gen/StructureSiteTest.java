package com.cosmicbreach.structure.gen;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cosmicbreach.world.Layer;
import com.cosmicbreach.world.gen.AetheriaTerrain;
import com.cosmicbreach.world.gen.ReachIslands;
import com.cosmicbreach.world.gen.SpireField;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Where the structures stand, on the terrain model: often enough that one placement region in four or so has
 * one (GDD 6.1's spacing is meant to be felt), and only where they fit: a Reliquary well inside a Spires island,
 * level, no natural spire reaching its plaza; an Observatory round a big asteroid with room above and below.
 */
class StructureSiteTest {
    @Test
    void sitesAreCommonAndSound() {
        for (long salt : new long[] {12345L, 987654321L}) {
            AetheriaTerrain t = AetheriaTerrain.forSalt(salt);
            Random r = new Random(salt);
            int n = 600;
            int reliquaries = 0;
            int observatories = 0;
            ReachIslands.Column col = new ReachIslands.Column();
            List<SpireField.Spire> spires = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                int x = r.nextInt(40000) - 20000;
                int z = r.nextInt(40000) - 20000;
                var rel = SpireReliquaryStructure.site(t, x, z);
                if (rel.isPresent()) {
                    reliquaries++;
                    var p = rel.get();
                    assertTrue(Math.abs(p.getX() - x) <= SpireReliquaryStructure.SEARCH && Math.abs(p.getZ() - z) <= SpireReliquaryStructure.SEARCH);
                    t.reach.sample(p.getX() + 0.5, p.getZ() + 0.5, col);
                    assertTrue(col.island && !col.isle.sunfield && col.edge >= ReliquaryLayout.PLAZA + 4);
                    assertTrue(Layer.at(p.getY()) == Layer.REACH && p.getY() + 62 < 470, "it fits under the Reach's ceiling: " + p);
                    int c = (int) ReliquaryLayout.PLAZA + 2;
                    t.spires.spiresTouching(p.getX() - c, p.getZ() - c, p.getX() + c, p.getZ() + c, spires);
                    for (SpireField.Spire sp : spires) {
                        double nx = Math.max(sp.minX, Math.min(sp.maxX, p.getX()));
                        double nz = Math.max(sp.minZ, Math.min(sp.maxZ, p.getZ()));
                        assertTrue(Math.hypot(nx - p.getX(), nz - p.getZ()) > ReliquaryLayout.PLAZA + 1, "a spire reaches the plaza");
                    }
                }
                Optional<GyreObservatoryStructure.Site> obs = GyreObservatoryStructure.site(t, x, z);
                if (obs.isPresent()) {
                    observatories++;
                    var s = obs.get();
                    ObservatoryLayout plan = ObservatoryLayout.of(i, s.radius(), s.headroom());
                    assertTrue(s.centre().getY() - plan.depth() >= GyreObservatoryStructure.FLOOR_Y, "the root stays over the Shear band");
                    assertTrue(s.centre().getY() + plan.height() <= GyreObservatoryStructure.CEILING_Y, "the dome stays under the Shear band");
                    assertTrue(s.radius() >= 9 && s.radius() <= 16);
                }
            }
            assertTrue(reliquaries > n / 6, "Reliquary sites in " + reliquaries + " of " + n + " regions");
            assertTrue(observatories > n / 6, "Observatory sites in " + observatories + " of " + n + " regions");
        }
    }
}

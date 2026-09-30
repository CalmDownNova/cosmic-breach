package com.cosmicbreach.client.guardian;

import com.cosmicbreach.guardian.GuardianBarPayload;
import com.cosmicbreach.guardian.colossus.RefractionPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The guardians' payloads on the client (main thread). */
public final class GuardianClientHandlers {
    private GuardianClientHandlers() {
    }

    public static void bar(GuardianBarPayload payload, IPayloadContext context) {
        GuardianBarHud.update(payload);
    }

    public static void refraction(RefractionPayload payload, IPayloadContext context) {
        RefractionView.update(payload);
    }
}

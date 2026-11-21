package fr.walid.acd.contribution.adapters.importing;

import com.bazaarvoice.jolt.Chainr;

/**
 * The transformation itself, entirely described by the versioned spec: changing the mapping is
 * changing a file under {@code contracts/import}, not changing Java.
 */
final class JoltMapping {
    private final Chainr chainr;

    private JoltMapping(Chainr chainr) {
        this.chainr = chainr;
    }

    static JoltMapping from(Object spec) {
        return new JoltMapping(Chainr.fromSpec(spec));
    }

    Object apply(Object input) {
        return chainr.transform(input);
    }
}

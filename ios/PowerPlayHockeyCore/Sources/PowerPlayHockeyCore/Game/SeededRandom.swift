/// Deterministic seeded PRNG (SplitMix64), reference type so it can be shared
/// between Simulation and AIController the way a single kotlin.random.Random
/// instance is shared on the Android side.
final class SeededRandom {
    private var state: UInt64

    init(seed: UInt64) {
        state = seed
    }

    private func nextUInt64() -> UInt64 {
        state = state &+ 0x9E3779B97F4A7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58476D1CE4E5B9
        z = (z ^ (z >> 27)) &* 0x94D049BB133111EB
        return z ^ (z >> 31)
    }

    /// Uniform float in [0, 1).
    func nextFloat() -> Float {
        Float(nextUInt64() >> 40) * (1.0 / Float(1 << 24))
    }

    func nextBool() -> Bool {
        nextUInt64() & 1 == 0
    }
}

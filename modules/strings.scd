// strings - five sympathetic strings, tuned to each other, ringing along
//
// After the sympathetic strings of the Mutable Instruments Rings. Five
// comb-filter strings at freq, tuned by structure to a set of intervals -
// unison, octaves, fifths, a minor chord, a major chord - and the sets
// morphed between. They ring with whatever comes in at in, or with a strike
// from trig; decay is how long, bright how much of the top the strike
// carries. A string in tune with what comes in rings at about the level it
// came in at, however long the decay.
//
// With voices it is that many sets of five strings, each control a value
// per voice, each set with a strike of its own.
(
	category: \filters,
	help: "five strings at freq (oct adds octaves) tuned by structure: unison (0), octaves (1), fifths (2), minor (3), major (4); ring with in or a strike from trig; decay, bright; with voices, five strings per voice",
	about: "Five sympathetic strings after Mutable Instruments Rings, tuned to each other in unison, octaves, fifths or a minor or major chord. They ring along with whatever comes in, or with a strike from trig.",
	params: (
		freq: "the pitch of the strings, in Hz",
		oct: "moves the pitch in octaves: 1 an octave up - patch a melody or a sequencer here",
		structure: "how the five strings are tuned: unison, octaves, fifths, minor, major; in between morphs",
		trig: "strikes the strings on its rising edge, on above 0.5 - press it, or patch a clock or gate",
		in: "a sound for the strings to ring with - noise, drums, a voice",
		bright: "a lowpass on what excites the strings, in Hz: down is a softer, darker ring",
		decay: "how long the strings ring, in seconds",
		level: "the output level"
	),
	labels: (structure: ["unison", "octaves", "fifths", "minor", "major"]),
	poly: { |n| {
		var freq = (\freq.kr(110 ! n, spec: \freq) * (2 ** \oct.kr(0 ! n, spec: [-4, 4]))).clip(20, 4000);
		var sets = [
			[1, 1.002, 0.998, 1.004, 0.996],
			[0.5, 1, 2, 4, 8],
			[1, 1.5, 2, 3, 4],
			[1, 1.1892, 1.4983, 2, 2.3784],
			[1, 1.2599, 1.4983, 2, 2.5198]
		];
		var structure = \structure.kr(2 ! n, spec: [0, 4]);
		var gate = \trig.kr(0 ! n) > 0.5;
		var strike = Decay2.ar(K2A.ar(Changed.kr(gate) * gate), 0.0005, 0.02) * WhiteNoise.ar(1 ! n);
		var exc = LPF.ar(\in.ar(0 ! n) + strike, \bright.kr(4000 ! n, spec: \freq));
		var decay = \decay.kr(3 ! n, spec: [0.1, 20, \exp]);
		5.collect { |k|
			var f = (freq * SelectX.kr(structure, sets.collect { |set| set[k] })).clip(20, 10000);
			// a string in tune with what comes in rings at about the level it came in at
			CombC.ar(exc, 0.05, f.reciprocal, decay) * (1 - (0.001 ** (f.reciprocal / decay)))
		}.sum * \level.kr(1 ! n, spec: [0, 4])
	} }
)

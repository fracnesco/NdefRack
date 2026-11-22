// crossmod - two signals put through each other, five ways on one knob
//
// After the Mutable Instruments Warps. a and b are combined by algo, morphed
// between its stops: a plain crossfade (0), ring modulation (1), the sum
// folded (2), xor - the sign of one flipped by the other, as loud as the
// louder (3), and a comparator, square while a is above b (4). gain drives
// both inputs first, which matters past the first stop. With voices, a
// pair for each: a and b go voice to voice, every control a value per voice.
(
	category: \effects,
	help: "a and b combined by algo (Warps): crossfade (0), ring modulation (1), folded sum (2), xor (3), comparator (4), morphed between; gain drives both inputs; level; with voices, one per voice",
	about: "Puts two signals through each other, five ways on one knob: a crossfade, ring modulation, a folded sum, xor and a comparator, morphing in between. Patch two oscillators or sounds into a and b for metallic, buzzy or digital tones.",
	params: (
		gain: "drives both inputs before they are combined - harder and dirtier past the crossfade",
		a: "the first signal",
		b: "the second signal",
		algo: "how a and b are combined: crossfade, ring modulation, folded sum, xor, comparator - in between morphs",
		level: "the output level"
	),
	labels: (algo: ["crossfade", "ring modulation", "folded sum", "xor", "comparator"]),
	poly: { |n| {
		var gain = \gain.kr(1 ! n, spec: [0.1, 10, \exp]);
		var a = \a.ar(0 ! n) * gain;
		var b = \b.ar(0 ! n) * gain;
		var xor = ((((a > 0) + (b > 0)) - 1).abs < 0.5) * 2 - 1 * a.abs.max(b.abs);
		var comparator = (a > b) * 2 - 1;
		LeakDC.ar(SelectX.ar(\algo.kr(0 ! n, spec: [0, 4]), [
			(a + b) * 0.5, a * b, (a + b).fold2(1), xor, comparator
		])) * \level.kr(0.5 ! n, spec: [0, 1])
	} }
)

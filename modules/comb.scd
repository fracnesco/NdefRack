// comb - a comb filter tuned to a pitch: a resonator, or a short echo
//
// A negative decay inverts the feedback, which leaves only the odd
// harmonics and drops the pitch an octave. With voices, a comb for each,
// every control a value per voice.
(
	category: \filters,
	help: "a comb filter tuned to freq (oct adds octaves), decay in seconds (negative for odd harmonics), mix with the dry signal",
	about: "A comb filter tuned to a pitch: it rings at freq like a plucked string or a metal tube, or with low pitches gives short echoes. Feed it noise bursts or drums; a negative decay leaves the odd harmonics and sounds an octave lower, hollow.",
	params: (
		freq: "the pitch it rings at, in Hz",
		oct: "moves the pitch up or down in octaves from freq - patch a sequencer here",
		in: "the sound to filter - noise, drums or anything with a bite",
		decay: "how long it rings, in seconds; negative keeps only odd harmonics and drops it an octave",
		mix: "0 dry, 1 all filtered"
	),
	poly: { |n| {
		var freq = (\freq.kr(220 ! n, spec: \freq) * (2 ** \oct.kr(0 ! n, spec: [-4, 4]))).clip(5, 20000);
		var in = \in.ar(0 ! n);
		var wet = CombC.ar(in, 0.2, freq.reciprocal, \decay.kr(1 ! n, spec: [-10, 10]));
		XFade2.ar(in, LeakDC.ar(wet) * 0.5, \mix.kr(0.5 ! n, spec: [0, 1]) * 2 - 1)
	} }
)

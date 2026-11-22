// ladder - a Moog-style four pole lowpass, self-oscillating at the top
//
// MoogFF loses bass as the resonance rises, as the hardware does; the
// output is lifted a little against it. With voices, a filter for each,
// every control a value per voice.
(
	category: \filters,
	help: "a 4-pole Moog ladder lowpass (MoogFF): cutoff freq, oct moves it in octaves, res sings near 1, drive",
	about: "A Moog-style four-pole lowpass: warm and round, losing a little bass as the resonance rises, and singing on its own near the top. Patch a wave in and an envelope or lfo into oct.",
	params: (
		freq: "the cutoff, in Hz",
		oct: "moves the cutoff in octaves: +1 an octave up - patch an lfo or envelope here to sweep it",
		res: "the resonance: higher rings at the cutoff, near 1 it whistles on its own",
		drive: "pushes the input into a soft clip before the filter: higher is grittier and louder",
		level: "the output level: 1 unchanged",
		in: "the sound to filter"
	),
	poly: { |n| {
		var freq = (\freq.kr(800 ! n, spec: \freq) * (2 ** \oct.kr(0 ! n, spec: [-4, 4]))).clip(20, 20000);
		var res = \res.kr(0.3 ! n, spec: [0, 1]);
		var in = (\in.ar(0 ! n) * \drive.kr(1 ! n, spec: [1, 10, \exp])).tanh;
		MoogFF.ar(in, freq, res * 3.95) * (1 + (res * 1.5)) * \level.kr(1 ! n, spec: [0, 2])
	} }
)

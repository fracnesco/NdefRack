// moogladder - Antti Huovilainen's model of the Moog ladder
// requires: MoogLadder
//
// sc3-plugins' MoogLadder. Left out of the library when sc3-plugins is
// not installed; ladder is the core one. With voices, a filter for each,
// every control a value per voice.
(
	category: \filters,
	help: "sc3-plugins' MoogLadder, a 4-pole lowpass modelled on the Moog: cutoff freq, oct moves it in octaves, res, drive",
	about: "A four-pole lowpass modelled closely on the Moog ladder: smooth, fat and resonant. Needs sc3-plugins; ladder is the built-in one. Patch a wave in and an envelope or lfo into oct.",
	params: (
		freq: "the cutoff, in Hz",
		oct: "moves the cutoff in octaves: +1 an octave up - patch an lfo or envelope here to sweep it",
		res: "the resonance: higher rings and whistles at the cutoff",
		drive: "pushes the input into a soft clip before the filter: higher is grittier and louder",
		level: "the output level: 1 unchanged",
		in: "the sound to filter"
	),
	poly: { |n| {
		var freq = (\freq.kr(800 ! n, spec: \freq) * (2 ** \oct.kr(0 ! n, spec: [-4, 4]))).clip(20, 20000);
		var in = (\in.ar(0 ! n) * \drive.kr(1 ! n, spec: [1, 10, \exp])).tanh;
		MoogLadder.ar(in, freq, \res.kr(0.3 ! n, spec: [0, 1])) * \level.kr(1 ! n, spec: [0, 2])
	} }
)

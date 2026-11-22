// dfm1 - a modelled analogue filter that screams when pushed
// requires: DFM1
//
// sc3-plugins' DFM1. type 0 is lowpass, 1 highpass; res past 1
// self-oscillates; drive is its own input gain. Left out of the library
// when sc3-plugins is not installed. With voices, a filter for each, every
// control a value per voice.
(
	category: \filters,
	help: "sc3-plugins' DFM1: lowpass (type 0) or highpass (1), res past 1 self-oscillates, drive is the input gain",
	about: "A modelled analogue filter, lowpass or highpass, that growls when driven and screams into self-oscillation when res goes past 1. Patch an envelope into oct for classic sweeps. Needs sc3-plugins.",
	params: (
		freq: "the cutoff, in Hz",
		oct: "moves the cutoff up or down in octaves from freq - patch an envelope or lfo here",
		in: "the sound to filter",
		res: "the resonance: higher rings more, past 1 it whistles on its own",
		drive: "the input gain: higher pushes the filter into distortion",
		type: "lowpass or highpass",
		noise: "a little noise in the filter, as in the analogue circuit - helps it start self-oscillating",
		level: "the output level"
	),
	labels: (type: ["lowpass", "highpass"]),
	poly: { |n| {
		var freq = (\freq.kr(1000 ! n, spec: \freq) * (2 ** \oct.kr(0 ! n, spec: [-4, 4]))).clip(20, 20000);
		DFM1.ar(\in.ar(0 ! n), freq, \res.kr(0.3 ! n, spec: [0, 1.2]),
			\drive.kr(1 ! n, spec: [0.1, 10, \exp]), \type.kr(0 ! n, spec: [0, 1, \lin, 1]),
			\noise.kr(0.0003 ! n, spec: [0, 0.01]))
			* \level.kr(0.7 ! n, spec: [0, 2])
	} }
)

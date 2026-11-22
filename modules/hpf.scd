// hpf - a resonant highpass, two poles
//
// With voices, a filter for each, every control a value per voice.
(
	category: \filters,
	help: "a resonant 2-pole highpass (RHPF): cutoff freq, oct moves it in octaves, res, drive",
	about: "A resonant highpass filter that takes away the lows and keeps the highs, thinning a sound out. Raise res for a peak at the cutoff; patch an lfo or envelope into oct to sweep it.",
	params: (
		freq: "the cutoff, in Hz",
		oct: "moves the cutoff in octaves: +1 an octave up - patch an lfo or envelope here to sweep it",
		res: "the resonance: higher rings and whistles at the cutoff",
		drive: "pushes the input into a soft clip before the filter: higher is grittier and louder",
		level: "the output level: 1 unchanged",
		in: "the sound to filter"
	),
	poly: { |n| {
		var freq = (\freq.kr(400 ! n, spec: \freq) * (2 ** \oct.kr(0 ! n, spec: [-4, 4]))).clip(20, 20000);
		var rq = \res.kr(0.3 ! n, spec: [0, 1]).linexp(0, 1, 1.4, 0.06);
		var in = (\in.ar(0 ! n) * \drive.kr(1 ! n, spec: [1, 10, \exp])).tanh;
		RHPF.ar(in, freq, rq) * \level.kr(1 ! n, spec: [0, 2])
	} }
)

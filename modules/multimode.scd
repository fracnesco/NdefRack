// multimode - lowpass, bandpass, highpass, one knob between them
//
// With voices, a filter for each, every control a value per voice.
(
	category: \filters,
	help: "a multimode biquad: mode morphs lowpass (0), bandpass (1), highpass (2); cutoff freq, oct moves it in octaves, res, drive",
	about: "A filter that morphs from lowpass to bandpass to highpass with one knob. Patch an lfo into mode for a filter that changes character as it moves, or into oct to sweep it.",
	params: (
		freq: "the cutoff, in Hz",
		oct: "moves the cutoff in octaves: +1 an octave up - patch an lfo or envelope here to sweep it",
		res: "the resonance: higher rings and whistles at the cutoff",
		drive: "pushes the input into a soft clip before the filter: higher is grittier and louder",
		level: "the output level: 1 unchanged",
		in: "the sound to filter",
		mode: "the filter type: 0 lowpass, 1 bandpass, 2 highpass - in between blends neighbours"
	),
	labels: (mode: ["lowpass", "bandpass", "highpass"]),
	poly: { |n| {
		var freq = (\freq.kr(1000 ! n, spec: \freq) * (2 ** \oct.kr(0 ! n, spec: [-4, 4]))).clip(20, 20000);
		var rq = \res.kr(0.3 ! n, spec: [0, 1]).linexp(0, 1, 1.4, 0.08);
		var in = (\in.ar(0 ! n) * \drive.kr(1 ! n, spec: [1, 10, \exp])).tanh;
		SelectX.ar(\mode.kr(0 ! n, spec: [0, 2]), [
			BLowPass.ar(in, freq, rq),
			BBandPass.ar(in, freq, rq) * rq.reciprocal.sqrt.min(4),
			BHiPass.ar(in, freq, rq)
		]) * \level.kr(1 ! n, spec: [0, 2])
	} }
)

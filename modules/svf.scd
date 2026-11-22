// svf - a state variable filter with all five outputs mixed by hand
// requires: SVF
//
// sc3-plugins' SVF: lowpass, bandpass, highpass, notch and peak are all
// there at once, and low, band, high, notch and peak are how much of each.
// Left out of the library when sc3-plugins is not installed; multimode is
// the core one. With voices, a filter for each, every control a value per
// voice.
(
	category: \filters,
	help: "sc3-plugins' SVF: cutoff freq, oct moves it in octaves, res; low, band, high, notch and peak mix its five outputs",
	about: "A state variable filter with all five responses at once - lowpass, bandpass, highpass, notch and peak - mixed by hand. Resonant and smooth; patch an lfo or envelope into oct to sweep it.",
	params: (
		freq: "the cutoff frequency, in Hz",
		oct: "moves the cutoff in octaves: 1 an octave up - patch an envelope or lfo here",
		in: "the sound to filter",
		res: "resonance: up rings louder at the cutoff",
		low: "how much of the lowpass output is in the mix",
		band: "how much of the bandpass output is in the mix",
		high: "how much of the highpass output is in the mix",
		notch: "how much of the notch output is in the mix",
		peak: "how much of the peak output is in the mix",
		level: "the output level"
	),
	pages: [filter: [\freq, \oct, \res, \level], mix: [\low, \band, \high, \notch, \peak]],
	poly: { |n| {
		var freq = (\freq.kr(1000 ! n, spec: \freq) * (2 ** \oct.kr(0 ! n, spec: [-4, 4]))).clip(20, 20000);
		SVF.ar(\in.ar(0 ! n), freq, \res.kr(0.3 ! n, spec: [0, 1]),
			\low.kr(1 ! n, spec: [0, 1]), \band.kr(0 ! n, spec: [0, 1]), \high.kr(0 ! n, spec: [0, 1]),
			\notch.kr(0 ! n, spec: [0, 1]), \peak.kr(0 ! n, spec: [0, 1]))
			* \level.kr(1 ! n, spec: [0, 2])
	} }
)

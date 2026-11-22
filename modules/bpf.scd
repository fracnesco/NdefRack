// bpf - a bandpass: only the band around freq gets through
//
// The narrower the band the less of the signal is in it, so the output is
// lifted as res narrows it, to keep the level about the same. With voices,
// a filter for each, every control a value per voice.
(
	category: \filters,
	help: "a bandpass around freq (BPF): oct moves it in octaves, res narrows it (and lifts the level to match), drive",
	about: "A bandpass filter: only the band around freq gets through, the rest falls away, giving a nasal or telephone-like sound. The level is lifted as the band narrows so it stays about as loud. Patch an lfo or envelope into oct to sweep it.",
	params: (
		freq: "the centre of the band, in Hz",
		oct: "moves the band up or down in octaves from freq - patch a pitch or an lfo here",
		res: "how narrow the band is: higher is narrower and more ringing, with the level lifted to match",
		in: "the sound to filter",
		drive: "pushes the input into soft clipping before the filter: 1 clean, higher dirtier",
		level: "the output level"
	),
	poly: { |n| {
		var freq = (\freq.kr(1000 ! n, spec: \freq) * (2 ** \oct.kr(0 ! n, spec: [-4, 4]))).clip(20, 20000);
		var rq = \res.kr(0.4 ! n, spec: [0, 1]).linexp(0, 1, 1.4, 0.03);
		var in = (\in.ar(0 ! n) * \drive.kr(1 ! n, spec: [1, 10, \exp])).tanh;
		BPF.ar(in, freq, rq) * rq.reciprocal.sqrt.min(5) * \level.kr(1 ! n, spec: [0, 2])
	} }
)

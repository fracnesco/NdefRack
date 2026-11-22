// notch - a band stop: everything but the band around freq
//
// With voices, a notch for each, every control a value per voice.
(
	category: \filters,
	help: "a notch at freq (BRF): oct moves it in octaves, res narrows it",
	about: "A notch filter: cuts a band around freq and lets everything else through. Sweep it with an lfo into oct for a hollow, moving sound, or park it on a frequency to remove.",
	params: (
		in: "the sound to filter",
		freq: "the centre of the cut band, in Hz",
		oct: "moves the notch in octaves: +1 an octave up - patch an lfo here to sweep it",
		res: "how narrow the notch is: higher cuts a thinner band",
		level: "the output level: 1 unchanged"
	),
	poly: { |n| {
		var freq = (\freq.kr(1000 ! n, spec: \freq) * (2 ** \oct.kr(0 ! n, spec: [-4, 4]))).clip(20, 20000);
		var rq = \res.kr(0.4 ! n, spec: [0, 1]).linexp(0, 1, 2, 0.05);
		BRF.ar(\in.ar(0 ! n), freq, rq) * \level.kr(1 ! n, spec: [0, 2])
	} }
)
